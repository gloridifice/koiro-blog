use wasm_bindgen::prelude::*;
use web_sys::{window, Element};

/// Attach click-to-animate listeners to elements matching `.touchable-avatar`.
#[wasm_bindgen]
pub fn init_touchable_avatar() -> Result<(), JsValue> {
    let doc = window()
        .and_then(|w| w.document())
        .ok_or_else(|| JsValue::from_str("no document"))?;

    let els = doc.query_selector_all(".touchable-avatar")?;

    for i in 0..els.length() {
        let el: Element = els
            .item(i)
            .ok_or_else(|| JsValue::from_str("missing element"))?
            .unchecked_into();

        let el_for_closure = el.clone();
        let cb = Closure::wrap(Box::new(move || {
            animate_avatar(&el_for_closure);
        }) as Box<dyn FnMut()>);

        el.add_event_listener_with_callback("click", cb.as_ref().unchecked_ref())?;
        cb.forget();
    }

    Ok(())
}

fn animate_avatar(el: &Element) {
    // Cancel existing animation if present
    if let Ok(anim_val) = js_sys::Reflect::get(el, &JsValue::from_str("_anim")) {
        if !anim_val.is_null() && !anim_val.is_undefined() {
            // Call .cancel() on the animation via Reflect
            let _ = js_sys::Reflect::get(&anim_val, &JsValue::from_str("cancel"))
                .and_then(|cancel_fn| {
                    if cancel_fn.is_function() {
                        js_sys::Reflect::apply(
                            &cancel_fn.unchecked_into::<js_sys::Function>(),
                            &anim_val,
                            &js_sys::Array::new(),
                        )
                    } else {
                        Err(JsValue::null())
                    }
                });
        }
    }

    // Build keyframes array
    let keyframes = js_sys::Array::new();

    let kf0 = js_sys::Object::new();
    js_sys::Reflect::set(&kf0, &JsValue::from_str("transform"), &JsValue::from_str("scaleY(1)")).unwrap();
    js_sys::Reflect::set(&kf0, &JsValue::from_str("offset"), &JsValue::from_f64(0.0)).unwrap();
    keyframes.push(&kf0);

    let kf1 = js_sys::Object::new();
    js_sys::Reflect::set(&kf1, &JsValue::from_str("transform"), &JsValue::from_str("scaleY(0.22)")).unwrap();
    js_sys::Reflect::set(&kf1, &JsValue::from_str("offset"), &JsValue::from_f64(0.4)).unwrap();
    keyframes.push(&kf1);

    let kf2 = js_sys::Object::new();
    js_sys::Reflect::set(&kf2, &JsValue::from_str("transform"), &JsValue::from_str("scaleY(1)")).unwrap();
    js_sys::Reflect::set(&kf2, &JsValue::from_str("offset"), &JsValue::from_f64(1.0)).unwrap();
    keyframes.push(&kf2);

    // Build options object
    let options = js_sys::Object::new();
    js_sys::Reflect::set(&options, &JsValue::from_str("duration"), &JsValue::from_f64(500.0)).unwrap();
    js_sys::Reflect::set(&options, &JsValue::from_str("easing"), &JsValue::from_str("cubic-bezier(.2,.7,.2,1)")).unwrap();
    js_sys::Reflect::set(&options, &JsValue::from_str("fill"), &JsValue::from_str("forwards")).unwrap();

    // Call el.animate(keyframes, options) via Reflect
    let anim = js_sys::Reflect::get(el, &JsValue::from_str("animate"))
        .ok()
        .and_then(|animate_fn| {
            if animate_fn.is_function() {
                let args = js_sys::Array::new();
                args.push(&keyframes);
                args.push(&options);
                js_sys::Reflect::apply(
                    &animate_fn.unchecked_into::<js_sys::Function>(),
                    el,
                    &args,
                )
                .ok()
            } else {
                None
            }
        });

    if let Some(anim) = anim {
        js_sys::Reflect::set(el, &JsValue::from_str("_anim"), &anim).unwrap();

        let el_clone = el.clone();
        let onfinish = Closure::wrap(Box::new(move || {
            js_sys::Reflect::set(&el_clone, &JsValue::from_str("_anim"), &JsValue::null())
                .unwrap();
        }) as Box<dyn FnMut()>);

        // Set onfinish callback
        js_sys::Reflect::set(
            &anim,
            &JsValue::from_str("onfinish"),
            onfinish.as_ref().unchecked_ref(),
        )
        .unwrap();
        onfinish.forget();
    }
}
