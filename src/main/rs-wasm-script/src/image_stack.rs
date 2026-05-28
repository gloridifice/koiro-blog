use wasm_bindgen::prelude::*;
use web_sys::{window, Element, HtmlElement, HtmlImageElement};
use std::cell::RefCell;
use std::rc::Rc;
use log::info;
use web_sys::console::info;

const CARD_WIDTH_STEP_PCT: f64 = 10.0;

/// Initialize all `.stack-container` galleries.
#[wasm_bindgen]
pub fn init_image_stack() -> Result<(), JsValue> {
    let doc = window()
        .and_then(|w| w.document())
        .ok_or_else(|| JsValue::from_str("no document"))?;

    let containers = doc.query_selector_all(".stack-container")?;

    for i in 0..containers.length() {
        let container_node = containers
            .item(i)
            .ok_or_else(|| JsValue::from_str("missing container"))?;
        let container: Element = container_node.unchecked_into();
        init_single_stack(&container)?;
    }

    // Window resize → recalc container heights
    let win = window().ok_or_else(|| JsValue::from_str("no window"))?;
    let resize_cb = Closure::wrap(Box::new(|| {
        let d = window().and_then(|w| w.document()).unwrap();
        let cs = d.query_selector_all(".stack-container").unwrap();
        for i in 0..cs.length() {
            if let Some(c_node) = cs.item(i) {
                let c: Element = c_node.unchecked_into();
                let _ = recalc_container_height(&c);
            }
        }
    }) as Box<dyn FnMut()>);
    win.add_event_listener_with_callback("resize", resize_cb.as_ref().unchecked_ref())?;
    resize_cb.forget();

    Ok(())
}

fn recalc_container_height(container: &Element) -> Result<(), JsValue> {
    let first_img = container.query_selector(".stack-card:first-child img")?;
    if let Some(img_node) = first_img {
        let img: HtmlImageElement = img_node.unchecked_into();
        let nw = img.natural_width();
        let nh = img.natural_height();
        if nw > 0 {
            let ow = container.client_width();
            if ow > 0 {
                let h = (ow as f64 * nh as f64 / nw as f64) as u32;
                set_style_property(container, "height", &format!("{}px", h))?;
            }
        }
    }
    Ok(())
}

fn init_single_stack(container: &Element) -> Result<(), JsValue> {
    let card_nodes = container.query_selector_all(".stack-card")?;
    let card_count = card_nodes.length();
    if card_count == 0 {
        return Ok(());
    }

    let mut cards_vec: Vec<HtmlElement> = Vec::with_capacity(card_count as usize);
    for i in 0..card_count {
        if let Some(el_node) = card_nodes.item(i) {
            cards_vec.push(el_node.unchecked_into::<HtmlElement>());
        }
    }

    // Random rotations for each card (-6 to 6 degrees) — doesn't change after init
    let rotations_vec: Vec<f64> = cards_vec
        .iter()
        .map(|_| (js_sys::Math::random() * 12.0) - 6.0)
        .collect();

    // Shared mutable state for cards; rotations are read-only
    let cards: Rc<RefCell<Vec<HtmlElement>>> = Rc::new(RefCell::new(cards_vec));
    let rotations: Rc<Vec<f64>> = Rc::new(rotations_vec);

    // Update container height based on first card's image
    update_container_height(container, &cards.borrow());

    // Apply card widths based on image heights
    apply_card_widths_by_height(&cards.borrow());

    // Store isAnimating flag on container element
    js_sys::Reflect::set(
        container,
        &JsValue::from_str("_isAnimating"),
        &JsValue::from_bool(false),
    )?;

    // Initial layout
    layout_cards(&cards.borrow(), &rotations);

    // Click handler — captures Rc clones so it sees current state
    let container_click = container.clone();
    let cards_click = cards.clone();
    let rotations_click = rotations.clone();
    let click_cb = Closure::wrap(Box::new(move || {
        next_card(&container_click, &cards_click, &rotations_click, "right");
    }) as Box<dyn FnMut()>);
    container.add_event_listener_with_callback("click", click_cb.as_ref().unchecked_ref())?;
    click_cb.forget();

    // --- touchstart handler ---
    let container_ts = container.clone();
    let ts_cb = Closure::wrap(Box::new(move |e: web_sys::TouchEvent| {
        let list = e.changed_touches();
        if let Some(t) = list.item(0) {
            js_sys::Reflect::set(
                &container_ts,
                &JsValue::from_str("_touchStartX"),
                &JsValue::from_f64(t.screen_x() as f64),
            )
            .unwrap();
            js_sys::Reflect::set(
                &container_ts,
                &JsValue::from_str("_touchStartY"),
                &JsValue::from_f64(t.screen_y() as f64),
            )
            .unwrap();
        }
    }) as Box<dyn FnMut(web_sys::TouchEvent)>);

    // Use passive:true via addEventListener options via Reflect
    add_event_listener_with_options(
        container,
        "touchstart",
        ts_cb.as_ref().unchecked_ref(),
        &[("passive", &JsValue::from_bool(true))],
    )?;
    ts_cb.forget();

    // --- touchend handler ---
    let container_te = container.clone();
    let cards_te = cards.clone();
    let rotations_te = rotations.clone();
    let te_cb = Closure::wrap(Box::new(move |e: web_sys::TouchEvent| {
        let list = e.changed_touches();
        if let Some(t) = list.item(0) {
            let start_x = js_sys::Reflect::get(
                &container_te,
                &JsValue::from_str("_touchStartX"),
            )
            .ok()
            .and_then(|v| v.as_f64())
            .unwrap_or(0.0);
            let start_y = js_sys::Reflect::get(
                &container_te,
                &JsValue::from_str("_touchStartY"),
            )
            .ok()
            .and_then(|v| v.as_f64())
            .unwrap_or(0.0);

            let diff_x = start_x - t.screen_x() as f64;
            let diff_y = start_y - t.screen_y() as f64;

            if diff_x.abs() > 50.0 && diff_x.abs() > diff_y.abs() {
                let dir = if diff_x > 0.0 { "left" } else { "right" };
                next_card(&container_te, &cards_te, &rotations_te, dir);
            }
        }
    }) as Box<dyn FnMut(web_sys::TouchEvent)>);
    container.add_event_listener_with_callback(
        "touchend",
        te_cb.as_ref().unchecked_ref(),
    )?;
    te_cb.forget();

    Ok(())
}

/// Add event listener with options via Reflect (since AddEventListenerOptions is not in web-sys 0.3).
fn add_event_listener_with_options(
    el: &Element,
    event: &str,
    callback: &js_sys::Function,
    options: &[(&str, &JsValue)],
) -> Result<(), JsValue> {
    let opts = js_sys::Object::new();
    for (key, val) in options {
        js_sys::Reflect::set(&opts, &JsValue::from_str(key), val)?;
    }
    let args = js_sys::Array::of2(&JsValue::from_str(event), callback);
    args.push(&opts);
    let add_el_fn = js_sys::Reflect::get(el, &JsValue::from_str("addEventListener"))?;
    js_sys::Reflect::apply(
        &add_el_fn.unchecked_into::<js_sys::Function>(),
        el,
        &args,
    )?;
    Ok(())
}

fn update_container_height(container: &Element, cards: &[HtmlElement]) {
    let first = match cards.first() {
        Some(c) => c,
        None => return,
    };
    let img_node = match first.query_selector("img") {
        Ok(Some(el)) => el,
        _ => return,
    };
    let img_el: HtmlImageElement = match img_node.dyn_into() {
        Ok(i) => i,
        Err(_) => return,
    };
    let img_for_closure = img_el.clone();

    let do_apply = {
        let container = container.clone();
        let cards: Vec<HtmlElement> = cards.iter().map(|c| c.clone()).collect();
        move || {
            let w = container.client_width();
            if w == 0 {
                return;
            }
            let nw = img_for_closure.natural_width();
            let nh = img_for_closure.natural_height();
            if nw == 0 || nh == 0 {
                return;
            }
            let win_h = window()
                .and_then(|w| w.inner_height().ok())
                .and_then(|v| v.as_f64())
                .unwrap_or(f64::MAX);
            let h = (w as f64 * nh as f64 / nw as f64).min(win_h * 0.75).min(nh as f64);
            info!("nw: {}, nh: {}, w: {}, h: {}, winh: {}, src: {}", nw, nh, w, h, win_h, img_for_closure.src());
            let h_px = format!("{}px", h as u32);
            for card in &cards {
                let _ = set_style_property(card, "height", &h_px);
            }
            let _ = set_style_property(&container, "height", &h_px);
        }
    };

    if img_el.complete() && img_el.natural_width() > 0 {
        do_apply();
    } else {
        let img_clone = img_el.clone();
        let cb = Closure::once(do_apply);
        img_clone
            .add_event_listener_with_callback("load", cb.as_ref().unchecked_ref())
            .unwrap();
        cb.forget();
    }
}

fn apply_card_widths_by_height(cards: &[HtmlElement]) {
    if cards.len() <= 1 {
        return;
    }

    let pairs: Vec<(&HtmlElement, HtmlImageElement)> = cards
        .iter()
        .filter_map(|card| {
            card.query_selector("img")
                .ok()
                .flatten()
                .and_then(|el| el.dyn_into::<HtmlImageElement>().ok())
                .map(|img| (card, img))
        })
        .collect();

    if pairs.len() <= 1 {
        return;
    }

    let do_apply = {
        let pairs: Vec<(HtmlElement, HtmlImageElement)> = pairs
            .iter()
            .map(|(c, i)| ((*c).clone(), i.clone()))
            .collect();
        move || -> bool {
            if !pairs
                .iter()
                .all(|(_, img)| img.complete() && img.natural_width() > 0)
            {
                return false;
            }
            let mut sorted: Vec<usize> = (0..pairs.len()).collect();
            sorted.sort_by(|&a, &b| {
                pairs[a]
                    .1
                    .natural_height()
                    .cmp(&pairs[b].1.natural_height())
            });

            let same_ratio = |a: &HtmlImageElement, b: &HtmlImageElement| -> bool {
                a.natural_width() as u64 * b.natural_height() as u64
                    == b.natural_width() as u64 * a.natural_height() as u64
            };

            let mut width_pct = 100.0;
            for (idx, &orig_idx) in sorted.iter().enumerate() {
                if idx > 0 && !same_ratio(&pairs[orig_idx].1, &pairs[sorted[idx - 1]].1) {
                    width_pct -= CARD_WIDTH_STEP_PCT;
                }
                let _ = set_style_property(
                    &pairs[orig_idx].0,
                    "max-width",
                    &format!("{}%", width_pct),
                );
            }
            true
        }
    };

    if !do_apply() {
        let mut pending = pairs
            .iter()
            .filter(|(_, img)| !(img.complete() && img.natural_width() > 0))
            .count();
        for (_, img) in &pairs {
            if img.complete() && img.natural_width() > 0 {
                continue;
            }
            let img_c = img.clone();
            let do_apply_c = do_apply.clone();
            let cb = Closure::wrap(Box::new(move || {
                pending -= 1;
                if pending == 0 {
                    do_apply_c();
                }
            }) as Box<dyn FnMut()>);
            img_c
                .add_event_listener_with_callback("load", cb.as_ref().unchecked_ref())
                .unwrap();
            cb.forget();
        }
    }
}

fn layout_cards(cards: &[HtmlElement], rotations: &[f64]) {
    let total = cards.len();
    for (i, card) in cards.iter().enumerate() {
        let is_active = i == 0;
        // Reset classes
        let cl = card.class_list();
        dom_token_list_remove_silent(&cl, "active");
        dom_token_list_remove_silent(&cl, "underneath");
        dom_token_list_remove_silent(&cl, "animating-out");

        if is_active {
            dom_token_list_add_silent(&cl, "active");
            let _ = set_style_property(card, "z-index", &total.to_string());
            let _ = set_style_property(
                card,
                "transform",
                "scale(1) rotate(0deg) translate3d(0, 0, 0)",
            );
        } else {
            dom_token_list_add_silent(&cl, "underneath");
            let _ = set_style_property(card, "z-index", &(total - i).to_string());

            let scale = (1.0 - (i as f64 * 0.05)).max(0.85);
            let translate_y = -(i as f64 * 8.0);
            let rot = card
                .dataset()
                .get("index")
                .and_then(|idx| idx.parse::<usize>().ok())
                .and_then(|idx| rotations.get(idx).copied())
                .unwrap_or(0.0);

            let _ = set_style_property(
                card,
                "transform",
                &format!(
                    "scale({}) translateY({}px) rotate({}deg)",
                    scale, translate_y, rot
                ),
            );
        }
    }
}

fn next_card(
    container: &Element,
    cards_rc: &Rc<RefCell<Vec<HtmlElement>>>,
    rotations: &[f64],
    direction: &str,
) {
    // Check isAnimating flag
    let is_animating = js_sys::Reflect::get(container, &JsValue::from_str("_isAnimating"))
        .ok()
        .and_then(|v| v.as_bool())
        .unwrap_or(false);

    let cards = cards_rc.borrow();
    if is_animating || cards.len() <= 1 {
        return;
    }

    js_sys::Reflect::set(
        container,
        &JsValue::from_str("_isAnimating"),
        &JsValue::from_bool(true),
    )
    .unwrap();

    let top_card = &cards[0];
    dom_token_list_remove_silent(&top_card.class_list(), "active");
    dom_token_list_add_silent(&top_card.class_list(), "animating-out");

    let win_w = window()
        .and_then(|w| w.inner_width().ok())
        .and_then(|v| v.as_f64())
        .unwrap_or(1024.0);
    let move_x = if direction == "right" { win_w } else { -win_w };
    let rotate_out: f64 = if direction == "right" { 30.0 } else { -30.0 };

    let _ = set_style_property(
        top_card,
        "transform",
        &format!(
            "translate3d({}px, -50px, 0) rotate({}deg) scale(1.1)",
            move_x, rotate_out
        ),
    );

    // Move remaining cards forward
    for (i, card) in cards.iter().skip(1).enumerate() {
        if i == 0 {
            let _ = set_style_property(card, "transform", "scale(1) rotate(0deg) translate3d(0, 0, 0)");
        } else {
            let scale = (1.0 - (i as f64 * 0.05)).max(0.85);
            let translate_y = -(i as f64 * 8.0);
            let rot = card
                .dataset()
                .get("index")
                .and_then(|idx| idx.parse::<usize>().ok())
                .and_then(|idx| rotations.get(idx).copied())
                .unwrap_or(0.0);
            let _ = set_style_property(
                card,
                "transform",
                &format!("scale({}) translateY({}px) rotate({}deg)", scale, translate_y, rot),
            );
        }
    }
    drop(cards); // release borrow before the timeout captures cards_rc

    // After 400ms, reorder cards (mutates the shared vec via Rc)
    let container_t = container.clone();
    let cards_rc_t = cards_rc.clone();
    let rotations_t = rotations.to_vec();
    let timeout_cb = Closure::once(Box::new(move || {
        let mut cards = cards_rc_t.borrow_mut();
        if !cards.is_empty() {
            let removed = cards.remove(0);
            let _ = set_style_property(&removed, "transition", "none");
            let _ = set_style_property(&removed, "transform", "scale(0.8) translateY(20px)");
            dom_token_list_remove_silent(&removed.class_list(), "animating-out");

            // Force reflow
            let _ = removed.offset_width();

            let _ = set_style_property(&removed, "transition", "");
            cards.push(removed);

            layout_cards(&cards, &rotations_t);
        }

        js_sys::Reflect::set(
            &container_t,
            &JsValue::from_str("_isAnimating"),
            &JsValue::from_bool(false),
        )
        .unwrap();
    }) as Box<dyn FnOnce()>);

    window()
        .unwrap()
        .set_timeout_with_callback_and_timeout_and_arguments_0(
            timeout_cb.as_ref().unchecked_ref(),
            400,
        )
        .unwrap();
    timeout_cb.forget();
}

// --- Helpers ---

fn set_style_property(el: &Element, prop: &str, value: &str) -> Result<(), JsValue> {
    let html_el: &HtmlElement = el.unchecked_ref();
    html_el.style().set_property(prop, value)
}

/// DOMTokenList::add — wraps the js_sys::Array API.
fn dom_token_list_add_silent(list: &web_sys::DomTokenList, token: &str) {
    let arr = js_sys::Array::of1(&JsValue::from_str(token));
    let _ = list.add(&arr);
}

/// DOMTokenList::remove — wraps the js_sys::Array API.
fn dom_token_list_remove_silent(list: &web_sys::DomTokenList, token: &str) {
    let arr = js_sys::Array::of1(&JsValue::from_str(token));
    let _ = list.remove(&arr);
}
