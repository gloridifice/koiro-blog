use wasm_bindgen::prelude::*;
use web_sys::{Element, window};

/// Attach click listeners to post-type filter buttons.
#[wasm_bindgen]
pub fn init_type_buttons() -> Result<(), JsValue> {
    let doc = window()
        .and_then(|w| w.document())
        .ok_or_else(|| JsValue::from_str("no document"))?;
    let buttons = doc.query_selector_all(".post_type_button")?;
    let selected_class = "is_selected";
    let hidden_class = "hidden";

    for i in 0..buttons.length() {
        if let Some(button_node) = buttons.item(i) {
            let btn: Element = button_node.unchecked_into();
            let btn_clone = btn.clone();
            let doc_clone = doc.clone();

            let cb = Closure::wrap(Box::new(move || {
                if btn_clone.class_list().contains(selected_class) {
                    return;
                }
                let all_buttons = doc_clone.query_selector_all(".post_type_button").unwrap();
                for j in 0..all_buttons.length() {
                    if let Some(other_node) = all_buttons.item(j) {
                        let other: Element = other_node.unchecked_into();
                        let _ = other.class_list().remove_1(selected_class);
                    }
                }
                let _ = btn_clone.class_list().add_1(selected_class);

                let btn_id = btn_clone.id();
                let self_previews_id = if let Some(idx) = btn_id.find('_') {
                    format!("{}_type_previews", &btn_id[..idx])
                } else {
                    format!("{}_type_previews", btn_id)
                };
                let all_previews = doc_clone.query_selector_all(".post_type_previews").unwrap();
                for j in 0..all_previews.length() {
                    if let Some(preview_node) = all_previews.item(j) {
                        let preview: Element = preview_node.unchecked_into();
                        if preview.id() != self_previews_id {
                            let _ = preview.class_list().add_1(hidden_class);
                        } else {
                            let _ = preview.class_list().remove_1(hidden_class);
                        }
                    }
                }
            }) as Box<dyn FnMut()>);

            btn.add_event_listener_with_callback("click", cb.as_ref().unchecked_ref())?;
            cb.forget();
        }
    }
    Ok(())
}
