use wasm_bindgen::prelude::*;
use web_sys::{window, Element};

/// Initialize scroll-reveal: adds "active" class to `.reveal` elements as they
/// scroll into view.
#[wasm_bindgen]
pub fn init_scroll_reveal() -> Result<(), JsValue> {
    let win = window().ok_or_else(|| JsValue::from_str("no window"))?;

    let reveal = Closure::wrap(Box::new(|| {
        let doc = match window().and_then(|w| w.document()) {
            Some(d) => d,
            None => return,
        };
        let reveals = match doc.query_selector_all(".reveal") {
            Ok(r) => r,
            Err(_) => return,
        };
        let window_height = window()
            .and_then(|w| {
                w.inner_height()
                    .ok()
                    .and_then(|v| v.as_f64())
            })
            .unwrap_or(0.0);

        for i in 0..reveals.length() {
            let el: Element = match reveals.item(i) {
                Some(node) => node.unchecked_into(),
                None => continue,
            };
            let rect = el.get_bounding_client_rect();
            let element_visible = -10.0;
            if rect.top() < window_height - element_visible {
                let arr = js_sys::Array::of1(&JsValue::from_str("active"));
                let _ = el.class_list().add(&arr);
            }
        }
    }) as Box<dyn FnMut()>);

    win.add_event_listener_with_callback("scroll", reveal.as_ref().unchecked_ref())?;
    win.add_event_listener_with_callback("load", reveal.as_ref().unchecked_ref())?;

    // Run immediately (load may have already fired)
    reveal
        .as_ref()
        .unchecked_ref::<js_sys::Function>()
        .call0(&JsValue::null())?;

    reveal.forget();

    Ok(())
}
