use wasm_bindgen::prelude::*;
use web_sys::HtmlImageElement;

mod avatar;
mod cookie;
mod image_stack;
mod scroll_reveal;
mod visual_mode;

// Re-export public init functions
pub use avatar::init_touchable_avatar;
pub use image_stack::init_image_stack;
pub use scroll_reveal::init_scroll_reveal;
pub use visual_mode::{init_type_buttons, init_visual_mode, init_visual_mode_all, init_visual_mode_buttons};

/// One-shot initializer for all ported scripts. Call once from JS on page load.
#[wasm_bindgen]
pub fn init_all() -> Result<(), JsValue> {
    init_visual_mode_all()?;
    init_touchable_avatar()?;
    init_image_stack()?;
    init_scroll_reveal()?;
    Ok(())
}

/// Sets height=100 on every `<img>` on the page. Legacy utility.
#[wasm_bindgen]
pub fn set_images_height_to_100() -> Result<(), JsValue> {
    let window = web_sys::window().ok_or("Global window not found")?;
    let document = window.document().ok_or("Document not found")?;

    let images = document.get_elements_by_tag_name("img");
    let length = images.length();

    for i in 0..length {
        if let Some(element) = images.item(i) {
            if let Ok(img) = element.dyn_into::<HtmlImageElement>() {
                img.set_height(100);
            }
        }
    }

    Ok(())
}
