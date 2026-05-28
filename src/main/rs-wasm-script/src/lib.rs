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
    wasm_logger::init(wasm_logger::Config::default());
    init_visual_mode_all()?;
    init_touchable_avatar()?;
    init_image_stack()?;
    init_scroll_reveal()?;
    Ok(())
}
