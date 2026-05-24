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


// ========= Touchable Avatar ==========
function setupTouchableAvatar(selector: string) {
    const els = document.querySelectorAll<HTMLElement>(selector);
    if (!els) return;
    els.forEach((el) => {
        el.addEventListener("click", () => {
            // 如果正在动画，先取消
            if ((el as any)._anim) {
                (el as any)._anim.cancel();
            }

            const keyframes: Keyframe[] = [
                {transform: "scaleY(1)", offset: 0},
                {transform: "scaleY(0.22)", offset: 0.4}, // 压缩
                {transform: "scaleY(1)", offset: 1}       // 回到原始
            ];

            const timing: KeyframeAnimationOptions = {
                duration: 500,
                easing: "cubic-bezier(.2,.7,.2,1)",
                fill: "forwards"
            };

            const anim = el.animate(keyframes, timing);
            (el as any)._anim = anim;

            anim.onfinish = () => {
                (el as any)._anim = null;
            };
        });
    })
}

document.addEventListener("DOMContentLoaded", () => {
    setupTouchableAvatar(".touchable-avatar");
});


// 卡片宽度根据图片高度排序递减的比例（百分比）
const CARD_WIDTH_STEP_PCT = 10;
// ========= Image Stack ========
document.addEventListener('DOMContentLoaded', () => {
    // 获取所有画廊容器
    const containers = document.querySelectorAll<HTMLElement>('.stack-container');

    containers.forEach((container: HTMLElement) => {
        let cards: HTMLElement[] = Array.prototype.slice.call(container.querySelectorAll<HTMLElement>('.stack-card'));

        // 为该容器的卡片生成特定随机旋转角度 (-6 到 6 度)
        const cardRotations: number[] = cards.map(() => (Math.random() * 12) - 6);

        var height = 0;

        // 根据第一张图片的宽高比设置容器高度
        function updateContainerHeight(): void {
            const firstCard = cards[0];
            const img = firstCard?.querySelector<HTMLImageElement>('img');
            if (!img) return;

            // 应用 stack-container 和 stack-card 的高度
            const applyHeight = () => {
                const w = container.offsetWidth;
                if (w === 0 || !img.naturalWidth || !img.naturalHeight) return;
                height = Math.min(w * img.naturalHeight / img.naturalWidth, window.innerHeight * 0.75)
                container.style.height =  `${height}px`;
                cards.forEach((it) => {
                    it.style.height = `${height}px`
                })
            };

            if (img.complete && img.naturalWidth > 0) {
                applyHeight();
            } else {
                img.addEventListener('load', applyHeight, {once: true});
            }
        }

        updateContainerHeight();

        // 根据图片高度排序，越高的图卡片越窄
        function applyCardWidthsByHeight(): void {
            const pairs: { card: HTMLElement; img: HTMLImageElement }[] = [];
            for (const card of cards) {
                const img = card.querySelector<HTMLImageElement>('img');
                if (img) pairs.push({card, img});
            }
            if (pairs.length <= 1) return;

            const sameRatio = (a: HTMLImageElement, b: HTMLImageElement): boolean =>
                a.naturalWidth * b.naturalHeight === b.naturalWidth * a.naturalHeight;

            const doApply = (): boolean => {
                if (!pairs.every(p => p.img.complete && p.img.naturalWidth > 0)) return false;
                // 按高度升序排列，最矮在前
                const sorted = [...pairs].sort((a, b) => a.img.naturalHeight - b.img.naturalHeight);
                let widthPct = 100;
                sorted.forEach(({ card, img }, i) => {
                    if (i > 0 && !sameRatio(img, sorted[i - 1].img)) {
                        widthPct -= CARD_WIDTH_STEP_PCT;
                    }
                    card.style.maxWidth = `${widthPct}%`;
                });
                return true;
            };

            if (!doApply()) {
                let pending = pairs.filter(p => !(p.img.complete && p.img.naturalWidth > 0)).length;
                pairs.forEach(({img}) => {
                    if (img.complete && img.naturalWidth > 0) return;
                    img.addEventListener('load', () => {
                        pending--;
                        if (pending === 0) doApply();
                    }, {once: true});
                });
            }
        }

        applyCardWidthsByHeight();

        let isAnimating: boolean = false;

        function layoutCards(): void {
            cards.forEach((card: HTMLElement, i: number) => {
                if (i === 0) {
                    card.className = 'stack-card active';
                    card.style.zIndex = cards.length.toString();
                    card.style.transform = `scale(1) rotate(0deg) translate3d(0, 0, 0)`;
                } else {
                    card.className = 'stack-card underneath';
                    card.style.zIndex = (cards.length - i).toString();

                    const scale: number = Math.max(0.85, 1 - (i * 0.05));
                    const translateY: number = i * -8;
                    const indexAttr: string | undefined = card.dataset.index;
                    const rotation: number = indexAttr ? cardRotations[parseInt(indexAttr, 10)] : 0;

                    card.style.transform = `scale(${scale}) translateY(${translateY}px) rotate(${rotation}deg)`;
                }
            });
        }

        function nextCard(direction: 'left' | 'right' = 'right'): void {
            if (isAnimating || cards.length <= 1) return;
            isAnimating = true;

            const topCard: HTMLElement = cards[0];
            topCard.classList.remove('active');
            topCard.classList.add('animating-out');

            const moveX: number = direction === 'right' ? window.innerWidth : -window.innerWidth;
            const rotateOut: number = direction === 'right' ? 30 : -30;

            topCard.style.transform = `translate3d(${moveX}px, -50px, 0) rotate(${rotateOut}deg) scale(1.1)`;

            setTimeout(() => {
                const removedCard: HTMLElement | undefined = cards.shift();
                if (!removedCard) return;

                removedCard.style.transition = 'none';
                removedCard.style.transform = `scale(0.8) translateY(20px)`;
                removedCard.classList.remove('animating-out');

                // 强制重绘
                void removedCard.offsetWidth;

                removedCard.style.transition = '';
                cards.push(removedCard);

                layoutCards();
                isAnimating = false;
            }, 400);

            const remainingCards = cards.slice(1);
            remainingCards.forEach((card: HTMLElement, i: number) => {
                if (i === 0) {
                    card.style.transform = `scale(1) rotate(0deg) translate3d(0, 0, 0)`;
                } else {
                    const scale: number = Math.max(0.85, 1 - (i * 0.05));
                    const translateY: number = i * -8;
                    const indexAttr: string | undefined = card.dataset.index;
                    const rotation: number = indexAttr ? cardRotations[parseInt(indexAttr, 10)] : 0;
                    card.style.transform = `scale(${scale}) translateY(${translateY}px) rotate(${rotation}deg)`;
                }
            });
        }

        // 点击事件
        container.addEventListener('click', () => {
            if (!isAnimating) nextCard('right');
        });

        // 触摸事件处理
        let touchStartX: number = 0;
        let touchStartY: number = 0;

        container.addEventListener('touchstart', (e: TouchEvent) => {
            touchStartX = e.changedTouches[0].screenX;
            touchStartY = e.changedTouches[0].screenY;
        }, {passive: true});

        container.addEventListener('touchend', (e: TouchEvent) => {
            const touchEndX: number = e.changedTouches[0].screenX;
            const touchEndY: number = e.changedTouches[0].screenY;

            const diffX: number = touchStartX - touchEndX;
            const diffY: number = touchStartY - touchEndY;

            if (Math.abs(diffX) > 50 && Math.abs(diffX) > Math.abs(diffY)) {
                if (diffX > 0) nextCard('left');
                else nextCard('right');
            }
        });

        // 初始布局
        layoutCards();
    });

    // 窗口大小改变时重新计算容器高度
    window.addEventListener('resize', () => {
        document.querySelectorAll<HTMLElement>('.stack-container').forEach(c => {
            const firstImg = c.querySelector<HTMLImageElement>('.stack-card:first-child img');
            if (!firstImg || !firstImg.naturalWidth) return;
            c.style.height = `${c.offsetWidth * firstImg.naturalHeight / firstImg.naturalWidth}px`;
        });
    });
});