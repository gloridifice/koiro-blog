use wasm_bindgen::prelude::*;
mod avatar;
mod image_stack;
mod post_filter;
mod scroll_reveal;

// Re-export public init functions
pub use avatar::init_touchable_avatar;
pub use image_stack::init_image_stack;
pub use post_filter::init_type_buttons;
pub use scroll_reveal::init_scroll_reveal;

/// One-shot initializer for all ported scripts. Call once from JS on page load.
#[wasm_bindgen]
pub fn init_all() -> Result<(), JsValue> {
    wasm_logger::init(wasm_logger::Config::default());
    init_type_buttons()?;
    init_touchable_avatar()?;
    init_image_stack()?;
    init_scroll_reveal()?;
    Ok(())
}
