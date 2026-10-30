```toml
title = "UI Framework | egui + wgpu + winit Environment Setup"
ai-translation = false
```

<aside>
💡 如果你发现这篇日志是英文不要感到奇怪，因为我想试着用英文写些东西（我的英文写作很烂）。

</aside>

![yeah!](images/Untitled.jpg)

yeah!

A GUI framework is convenient for me to debug and development. I chose [egui](https://github.com/emilk/egui).

> **egui is an easy-to-use GUI in pure Rust**
> 

There are few example for wgpu + winit + egui. I found [that example](https://github.com/hasenbanck/egui_example) but it is outdated. And all egui official examples are written in eframe. 

I finally found a example in egui github discussions, successfully used egui.

[Simple Rust/Cargo project example using egui+winit+wgpu? · emilk/egui · Discussion #3067](https://github.com/emilk/egui/discussions/3067)

My render function adjusted:

```rust
fn render(runtime: &mut Runtime) {
    let surface = &runtime.context.surface;
    let device = &runtime.context.device;
    let queue = &runtime.context.queue;
    let output = surface.get_current_texture().unwrap();
    let view = output
        .texture
        .create_view(&wgpu::TextureViewDescriptor::default());
    let mut encoder = device.create_command_encoder(&wgpu::CommandEncoderDescriptor {
        label: Some("Egui Render Encoder"),
    });

		// Render models
    runtime.nodes.iter().for_each(|it| {
        runtime
            .pass
            .draw(
                surface,
                device,
                queue,
                &mut encoder,
                &view,
                it,
            )
            .expect("Draw failed!");
    });
		
		// Render egui UI
    let size = runtime.window.inner_size();
    runtime.egui_renderer.draw(device, queue, &runtime.window, &mut encoder, &view,
        &ScreenDescriptor {
            size_in_pixels: [ size.width, size.height ],
            pixels_per_point: pixels_per_point(
                &runtime.egui_renderer.context,
                &runtime.window,
            ),
        },
        |mut ui| {
            egui::Window::new("Settings")
                .resizable(true)
                .vscroll(true)
                .default_open(false)
                .show(&ui, |mut ui| {
                    ui.label("Window!");
                    ui.add(egui::Slider::new(&mut runtime.camera.fovy, 10f32..=120f32).text("fovy"))
                });
        },
    );

    queue.submit(Some(encoder.finish()));
    output.present();
}
```