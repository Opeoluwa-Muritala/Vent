(function () {
    "use strict";
    let container = null;
    let video = null;
    let stream = null;
    let timer = null;
    let generation = 0;
    let pending = "";

    function button(label, action) {
        const control = document.createElement("button");
        control.type = "button";
        control.textContent = label;
        control.style.cssText = "min-height:48px;min-width:48px;padding:12px 16px;border:1px solid #4A4F5A;border-radius:24px;background:#272B33;color:#EEF0F4;font:500 14px system-ui;pointer-events:auto";
        control.addEventListener("click", action);
        return control;
    }

    function stop() {
        generation++;
        clearTimeout(timer);
        timer = null;
        if (stream) stream.getTracks().forEach(track => track.stop());
        stream = null;
        if (container) container.remove();
        container = null;
        video = null;
        pending = "";
    }

    async function start() {
        stop();
        const active = generation;
        container = document.createElement("div");
        container.setAttribute("aria-label", "Session check-in camera");
        container.style.cssText = "position:fixed;inset:0;z-index:10;background:#14161A;color:#EEF0F4;overflow:hidden;font:16px system-ui";
        video = document.createElement("video");
        video.muted = true;
        video.autoplay = true;
        video.playsInline = true;
        video.setAttribute("aria-hidden", "true");
        video.style.cssText = "width:100%;height:100%;object-fit:cover";
        container.appendChild(video);
        const message = document.createElement("p");
        message.textContent = "Point at the session QR code.";
        message.setAttribute("role", "status");
        message.style.cssText = "position:absolute;top:64px;left:24px;right:24px;text-align:center;padding:12px;background:#14161AE0;border-radius:8px";
        container.appendChild(message);
        const back = button("Back", () => { pending = "__close__"; });
        back.style.position = "absolute";
        back.style.top = "max(16px,env(safe-area-inset-top))";
        back.style.left = "16px";
        container.appendChild(back);
        const frame = document.createElement("div");
        frame.setAttribute("aria-hidden", "true");
        frame.style.cssText = "position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:min(240px,70vw);aspect-ratio:1;pointer-events:none";
        ["top:0;left:0;border-top", "top:0;right:0;border-top", "bottom:0;left:0;border-bottom", "bottom:0;right:0;border-bottom"].forEach((position, index) => {
            const corner = document.createElement("span");
            const pieces = position.split(";");
            corner.style.cssText = "position:absolute;width:28px;height:28px;" + pieces.slice(0, 2).join(";") + ";" + pieces[2] + ":4px solid #B9A7FF;" +
                (index % 2 === 0 ? "border-left" : "border-right") + ":4px solid #B9A7FF";
            frame.appendChild(corner);
        });
        container.appendChild(frame);
        const controls = document.createElement("div");
        controls.style.cssText = "position:absolute;bottom:max(16px,env(safe-area-inset-bottom));left:16px;right:16px;display:flex;justify-content:space-between;gap:8px";
        let torchOn = false;
        const torch = button("Torch unavailable", async () => {
            const track = stream && stream.getVideoTracks()[0];
            if (!track) return;
            torch.disabled = true;
            try {
                await track.applyConstraints({ advanced: [{ torch: !torchOn }] });
                torchOn = !torchOn;
                torch.textContent = torchOn ? "Torch on" : "Torch off";
                torch.setAttribute("aria-pressed", String(torchOn));
            } catch (_) {
                message.textContent = "Torch unavailable. You can still scan the code.";
            } finally {
                torch.disabled = false;
            }
        });
        torch.disabled = true;
        controls.appendChild(torch);
        controls.appendChild(button("Enter code instead", () => { pending = "__manual__"; }));
        container.appendChild(controls);
        document.body.appendChild(container);
        if (!window.isSecureContext || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            message.textContent = "Camera access requires a supported browser on HTTPS. Enter the code instead.";
            frame.hidden = true;
            return;
        }
        try {
            const capture = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: "environment" } }, audio: false });
            if (active !== generation) {
                capture.getTracks().forEach(track => track.stop());
                return;
            }
            stream = capture;
            video.srcObject = stream;
            await video.play();
            if (active !== generation) return;
            const track = stream.getVideoTracks()[0];
            const capabilities = track.getCapabilities ? track.getCapabilities() : {};
            torch.disabled = !capabilities.torch;
            torch.textContent = capabilities.torch ? "Torch off" : "Torch unavailable";
            let detector = null;
            if ("BarcodeDetector" in window) {
                try { detector = new window.BarcodeDetector({ formats: ["qr_code"] }); } catch (_) {}
            }
            const canvas = document.createElement("canvas");
            const context = canvas.getContext("2d", { willReadFrequently: true });
            async function scan() {
                if (active !== generation || !video) return;
                if (video.readyState >= 2 && context) {
                    const ratio = Math.min(1, 640 / video.videoWidth);
                    canvas.width = Math.max(1, Math.round(video.videoWidth * ratio));
                    canvas.height = Math.max(1, Math.round(video.videoHeight * ratio));
                    context.drawImage(video, 0, 0, canvas.width, canvas.height);
                    let value = "";
                    try {
                        if (detector) {
                            const results = await detector.detect(canvas);
                            value = results.length ? results[0].rawValue : "";
                        } else if (typeof window.jsQR === "function") {
                            const pixels = context.getImageData(0, 0, canvas.width, canvas.height);
                            const result = window.jsQR(pixels.data, pixels.width, pixels.height, { inversionAttempts: "dontInvert" });
                            value = result ? result.data : "";
                        } else {
                            message.textContent = "QR scanning is unavailable in this browser. Enter the code instead.";
                        }
                        if (active !== generation) return;
                        if (/^[0-9]{6}$/.test(value.trim())) {
                            pending = value.trim();
                            return;
                        }
                    } catch (_) {
                        message.textContent = "Could not read the QR code. Try again or enter the code instead.";
                    }
                }
                timer = setTimeout(scan, 250);
            }
            timer = setTimeout(scan, 250);
        } catch (_) {
            if (active !== generation) return;
            message.textContent = "Camera access is unavailable. Allow camera access in your browser's site settings, then reopen the scanner, or enter the code instead.";
            frame.hidden = true;
        }
    }

    window.eventsCamera = {
        start,
        stop,
        read: function () { const value = pending; pending = ""; return value; },
        bounds: function (left, top, width, height) {
            if (!container) return;
            Object.assign(container.style, { inset: "auto", left: left + "px", top: top + "px", width: width + "px", height: height + "px" });
        }
    };
})();
