use std::cell::RefCell;
use std::rc::Rc;
use wasm_bindgen::prelude::*;
use web_sys::{Element, HtmlElement, window};

const CYCLE_DURATION_MS: i32 = 400;

/// Initialize all `.stack-container` galleries. CSS owns image sizing so mixed
/// aspect ratios, lazy loading and viewport changes share the same layout.
#[wasm_bindgen]
pub fn init_image_stack() -> Result<(), JsValue> {
    let doc = window()
        .and_then(|w| w.document())
        .ok_or_else(|| JsValue::from_str("no document"))?;

    let containers = doc.query_selector_all(".stack-container")?;
    for i in 0..containers.length() {
        if let Some(node) = containers.item(i) {
            init_single_stack(&node.unchecked_into::<Element>())?;
        }
    }
    Ok(())
}

fn init_single_stack(container: &Element) -> Result<(), JsValue> {
    let card_nodes = container.query_selector_all(".stack-card")?;
    let cards: Vec<HtmlElement> = (0..card_nodes.length())
        .filter_map(|i| card_nodes.item(i))
        .map(|node| node.unchecked_into())
        .collect();
    if cards.is_empty() {
        return Ok(());
    }
    layout_cards(&cards);
    let cards = Rc::new(RefCell::new(cards));
    let is_animating = Rc::new(RefCell::new(false));

    let container_click = container.clone();
    let cards_click = cards.clone();
    let animating_click = is_animating.clone();
    let click_cb = Closure::wrap(Box::new(move || {
        next_card(&container_click, &cards_click, &animating_click, 1.0);
    }) as Box<dyn FnMut()>);
    container.add_event_listener_with_callback("click", click_cb.as_ref().unchecked_ref())?;
    click_cb.forget();

    let touch_start = Rc::new(RefCell::new(None));
    let start_ts = touch_start.clone();
    let ts_cb = Closure::wrap(Box::new(move |event: web_sys::TouchEvent| {
        *start_ts.borrow_mut() = event
            .changed_touches()
            .item(0)
            .map(|touch| (touch.screen_x(), touch.screen_y()));
    }) as Box<dyn FnMut(web_sys::TouchEvent)>);
    add_passive_listener(container, "touchstart", ts_cb.as_ref().unchecked_ref())?;
    ts_cb.forget();

    let container_te = container.clone();
    let te_cb = Closure::wrap(Box::new(move |event: web_sys::TouchEvent| {
        let start = touch_start.borrow_mut().take();
        if let (Some((start_x, start_y)), Some(touch)) = (start, event.changed_touches().item(0)) {
            let dx = touch.screen_x() - start_x;
            let dy = touch.screen_y() - start_y;
            if dx.abs() > 50 && dx.abs() > dy.abs() {
                next_card(
                    &container_te,
                    &cards,
                    &is_animating,
                    if dx > 0 { 1.0 } else { -1.0 },
                );
            }
        }
    }) as Box<dyn FnMut(web_sys::TouchEvent)>);
    container.add_event_listener_with_callback("touchend", te_cb.as_ref().unchecked_ref())?;
    te_cb.forget();
    Ok(())
}

fn add_passive_listener(
    element: &Element,
    event: &str,
    callback: &js_sys::Function,
) -> Result<(), JsValue> {
    let options = js_sys::Object::new();
    js_sys::Reflect::set(&options, &JsValue::from_str("passive"), &JsValue::TRUE)?;
    let args = js_sys::Array::of2(&JsValue::from_str(event), callback);
    args.push(&options);
    let add_listener = js_sys::Reflect::get(element, &JsValue::from_str("addEventListener"))?;
    js_sys::Reflect::apply(
        &add_listener.unchecked_into::<js_sys::Function>(),
        element,
        &args,
    )?;
    Ok(())
}

fn layout_cards(cards: &[HtmlElement]) {
    for (index, card) in cards.iter().enumerate() {
        // Repeat four bounded positions rather than letting long galleries drift
        // out of their container. Position follows depth, not image identity.
        let position = if index == 0 { 0 } else { (index - 1) % 4 + 1 };
        let _ = card.dataset().set("position", &position.to_string());
        let _ = card
            .style()
            .set_property("z-index", &(cards.len() - index).to_string());
        let classes = card.class_list();
        let _ = classes.remove_1("animating-out");
        let _ = classes.toggle_with_force("active", index == 0);
    }
}

fn next_card(
    container: &Element,
    cards: &Rc<RefCell<Vec<HtmlElement>>>,
    is_animating: &Rc<RefCell<bool>>,
    direction: f64,
) {
    if *is_animating.borrow() || cards.borrow().len() <= 1 {
        return;
    }
    *is_animating.borrow_mut() = true;

    {
        let cards = cards.borrow();
        let top = &cards[0];
        let _ = top
            .style()
            .set_property("--exit-direction", &direction.to_string());
        let _ = top.class_list().remove_1("active");
        let _ = top.class_list().add_1("animating-out");
        layout_cards(&cards[1..]);
    }

    let cards = cards.clone();
    let is_animating = is_animating.clone();
    let container = container.clone();
    let callback = Closure::once(Box::new(move || {
        let mut cards = cards.borrow_mut();
        let outgoing = cards[0].clone();
        // Return the faded photo directly to the back without flying across the
        // newly active photo. Grid sizing stays unchanged throughout the cycle.
        let _ = outgoing.style().set_property("transition", "none");
        cards.rotate_left(1);
        layout_cards(&cards);
        let _ = container.unchecked_ref::<HtmlElement>().offset_width();
        let _ = outgoing.style().remove_property("transition");
        *is_animating.borrow_mut() = false;
    }) as Box<dyn FnOnce()>);

    window()
        .unwrap()
        .set_timeout_with_callback_and_timeout_and_arguments_0(
            callback.as_ref().unchecked_ref(),
            CYCLE_DURATION_MS,
        )
        .unwrap();
    callback.forget();
}
