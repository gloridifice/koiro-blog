function getRandomLetter(): string {
    const code = Math.random() < 0.5
        ? Math.floor(Math.random() * 26) + 65  // A-Z (65-90)
        : Math.floor(Math.random() * 26) + 97; // a-z (97-122)
    return String.fromCharCode(code);
}

function getRandomString(length: number): string {
    return Array.from({length}, getRandomLetter).join('');
}

document.addEventListener("DOMContentLoaded", () => {
    const targets = document.getElementsByClassName("cursor_follower");

    if (!targets || targets.length == 0) return;

    var lastX = 0;
    var lastY = 0;
    document.addEventListener("mousemove", (event) => {
        for (let target of targets) {
            if (target instanceof HTMLElement) {
                target.style.position = "absolute"; // 让元素支持定位
                target.style.fontFamily = "monospace";
                let fontSize = 32
                const rect = target.getBoundingClientRect();
                target.style.fontSize = `${fontSize}px`;
                target.style.whiteSpace = 'pre-line';
                let xSize = fontSize / 2
                let ySize = fontSize
                let x = Math.floor(event.pageX / xSize) * xSize
                let y = Math.floor(event.pageY / ySize) * ySize
                if (lastX != x || lastY != y) {
                    target.style.left = `${x - rect.width / 2}px`;
                    target.style.top = `${y - rect.height / 2}px`;
                    target.textContent = Array.from({length: 5}, (_, i) => getRandomString(10) + '\n').join('');
                }
                lastX = x;
                lastY = y;
            }
        }
    });
});
