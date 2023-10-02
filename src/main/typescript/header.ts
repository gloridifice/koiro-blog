enum VisualMode {
    Light, Dark
}

var currentVisualMode = VisualMode.Light
let visualModeCSSId = "visual_mode_css"


//init visual mode
let visualMode = document.getElementById(visualModeCSSId) as HTMLLinkElement
if (visualMode == null) {
    visualMode = document.createElement("link") as HTMLLinkElement
    visualMode.id = visualModeCSSId
    visualMode.rel = "stylesheet"
    document.head.append(visualMode)

    setVisualMode(getInitVisualMode())
}

//add button listener

window.addEventListener("load", ev => {
    addVisualButtonEvent()
    addTypeButtonEvent()
})

function addTypeButtonEvent() {
    let typeButtons = document.querySelectorAll(".post_type_button")

    let selectedClassName = "is_selected"
    typeButtons.forEach(button => {
        button.addEventListener("click", evt => {
            let selfIsSelected = button.classList.contains(selectedClassName)
            if (selfIsSelected) return

            //remove other selected
            typeButtons.forEach(other => {
                other.classList.remove(selectedClassName)
            })

            //add self selected
            button.classList.add(selectedClassName)

            let selfPreviewsId = button.id.split('_')[0] + "_type_previews"
            let hiddenClassName = "hidden"
            //display post previews
            document.querySelectorAll(".post_type_previews").forEach(previews => {
                if (previews.id != selfPreviewsId) {
                    if (!previews.classList.contains(hiddenClassName))
                        previews.classList.add(hiddenClassName)
                } else {
                    if (previews.classList.contains(hiddenClassName))
                        previews.classList.remove(hiddenClassName)
                }
            })
        })
    })
}

function addVisualButtonEvent() {
    var its = document.querySelectorAll(".switch_visual_mode_button")

    console.log(its)
    its?.forEach(it => {
        it.addEventListener("click", ev1 => {
            switchVisualMode()
        })
    })
}

function switchVisualMode() {
    if (currentVisualMode === VisualMode.Light) {
        setVisualMode(VisualMode.Dark)
    } else {
        setVisualMode(VisualMode.Light)
    }
}

function getInitVisualMode(): VisualMode {
    let modeCookie = readCookie("visual_mode")
    if (modeCookie != null) {
        return modeCookie === "light" ? VisualMode.Light : VisualMode.Dark
    }
    let sysMode = getSystemVisualMode()
    createCookie("visual_mode", sysMode === VisualMode.Light ? "light" : "dark", null)
    return sysMode
}

function getSystemVisualMode(): VisualMode {
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)')) {
        return VisualMode.Dark
    }
    return VisualMode.Light
}

function setVisualMode(mode: VisualMode) {
    let visualMode = document.getElementById(visualModeCSSId) as HTMLLinkElement
    if (mode == VisualMode.Light) {
        visualMode.href = "/assets/css/color_scheme.light_mode.css"
    } else {
        visualMode.href = "/assets/css/color_scheme.dark_mode.css"
    }
    currentVisualMode = mode
    createCookie("visual_mode", mode === VisualMode.Light ? "light" : "dark", null)
}

function createCookie(name, value, days) {
    if (days) {
        var date = new Date();
        date.setTime(date.getTime() + (days * 24 * 60 * 60 * 1000));
        var expires = "; expires=" + date.toDateString();
    } else var expires = "";
    document.cookie = name + "=" + value + expires + "; path=/";
}

function readCookie(name) {
    var nameEQ = name + "=";
    var ca = document.cookie.split(';');
    for (var i = 0; i < ca.length; i++) {
        var c = ca[i];
        while (c.charAt(0) == ' ') c = c.substring(1, c.length);
        if (c.indexOf(nameEQ) == 0) return c.substring(nameEQ.length, c.length);
    }
    return null;
}

function eraseCookie(name) {
    createCookie(name, "", -1);
}
