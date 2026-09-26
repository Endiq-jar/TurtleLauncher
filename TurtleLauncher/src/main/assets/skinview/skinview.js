const container = document.getElementById("skin-container");

const getWidth = () => container.clientWidth || window.innerWidth || 300;
const getHeight = () => container.clientHeight || window.innerHeight || 400;

const skinViewer = new skinview3d.SkinViewer({
    canvas: document.createElement("canvas"),
    width: getWidth(),
    height: getHeight()
});

container.appendChild(skinViewer.canvas);

//Reference: the Modrinth launcher default idle animation
//https://github.com/modrinth/code/blob/e71a8c10fac3eda05ff9bc34381178f3d11c41de/packages/assets/models/slim-player.gltf#L1653-L1755
class IdleAnimation extends skinview3d.PlayerAnimation {
    constructor() {
        super();
        this.elapsed = 0;
        this.maxDelta = 0.05;
    }

    getCapeRotation(t) {
        const wave1 = Math.sin(t * Math.PI * 2 * 0.3);
        const wave2 = Math.sin(t * Math.PI * 2 * 0.25) * 0.3;

        let normalized = (wave1 + wave2 + 1.3) / 2.6;
        normalized = Math.max(0, Math.min(1, normalized));

        const angleX = 0.15 + normalized * 0.15;

        const waveZ = Math.sin(t * Math.PI * 2 * 0.35);
        const angleZ = 0.03 + (waveZ + 1) / 2 * 0.03;

        return { x: angleX, z: angleZ };
    }

    animate(player, delta) {
        const dt = Math.min(delta, this.maxDelta);
        this.elapsed += dt;

        const t = this.elapsed;

        const breathe = Math.sin(t * 1.2) * 0.04;

        player.skin.body.position.y = Math.sin(t * 2.2) * -0.1 - 6.1;
        player.skin.head.position.y = Math.sin(t * 2.2) * -0.1 - 0.1;

        player.skin.body.rotation.x = Math.sin(t * 2.2) * 0.02;
        player.skin.body.rotation.z = Math.sin(t * 1.5) * 0.01;

        player.skin.head.rotation.y = Math.sin(t * 0.6) * 0.1;
        player.skin.head.rotation.x = Math.sin(t * 2.2) * 0.04;
        player.skin.head.rotation.z = Math.sin(t * 1.5) * 0.01;

        const armX = Math.sin(t * 2.2) * 0.05;
        const armZ = (Math.sin(t * 1.8) + 1) / 2 * -0.05;

        player.skin.rightArm.rotation.x = armX;
        player.skin.leftArm.rotation.x = armX;

        player.skin.rightArm.rotation.z = armZ;
        player.skin.leftArm.rotation.z = -armZ;

        if (player.cape) {
            const capeRot = this.getCapeRotation(t);
            player.cape.rotation.x = capeRot.x;
            player.cape.rotation.z = capeRot.z;
        }
    }

    reset() {
        this.elapsed = 0;
    }
}

function startAnim(name, speed) {
    if (typeof name !== 'string' || name.trim() === '') {
        console.warn('The animation name must be a non-empty string');
        return;
    }

    let speed0 = null;
    if (typeof speed === 'number' && !isNaN(speed) && speed > 0) {
        speed0 = speed;
    }

    let anim;
    switch (name) {
        case "DefaultIdle":
            anim = new skinview3d.IdleAnimation();
            break;
        case "NewIdle":
            anim = new IdleAnimation();
            break;
        case "Walking":
            anim = new skinview3d.WalkingAnimation();
            break;
        case "Running":
            anim = new skinview3d.RunningAnimation();
            break;
        case "Flying":
            anim = new skinview3d.FlyingAnimation();
            break;
        case "Wave":
            anim = new skinview3d.WaveAnimation();
            break;
        case "Crouch":
            anim = new skinview3d.CrouchAnimation();
            break;
        case "Hit":
            anim = new skinview3d.HitAnimation();
            break;
        default:
            return;
    }

    skinViewer.animation = anim;
    if (speed0 !== null && skinViewer.animation) {
        skinViewer.animation.speed = speed0;
    }
}

skinViewer.controls.enableRotate = true;
skinViewer.controls.enableZoom = false;
skinViewer.controls.enablePan = false;

//Record the default camera position and controller target
const defaultCameraPos = skinViewer.camera.position.clone();
const defaultControlsTarget = skinViewer.controls.target.clone();

function updateDefaultCameraPosition() {
    defaultCameraPos.copy(skinViewer.camera.position);
    defaultControlsTarget.copy(skinViewer.controls.target);
}

function setAzimuthAndPitch(azimuthDeg, pitchDeg, distance = 60) {
    const controls = skinViewer.controls;
    const target = controls.target;

    const azimuth = azimuthDeg * Math.PI / 180;
    const pitch = pitchDeg * Math.PI / 180;

    const x = distance * Math.cos(pitch) * Math.sin(azimuth);
    const y = distance * Math.sin(pitch);
    const z = distance * Math.cos(pitch) * Math.cos(azimuth);

    skinViewer.camera.position.set(target.x + x, target.y + y, target.z + z);
    controls.update();

    updateDefaultCameraPosition();
}

setAzimuthAndPitch(0, 10);

// Ensure OrbitControls uses the same target, overriding the default lookAt
if (skinViewer.controls) {
    skinViewer.controls.update();
} else if (skinViewer.camera.lookAt) {
    skinViewer.camera.lookAt(0, 16, 0);
}

let resetAnimationId = null;

// Listen for double-click events on the container
container.addEventListener("dblclick", () => {
    // If a recenter animation is already running, cancel it first
    if (resetAnimationId) {
        cancelAnimationFrame(resetAnimationId);
    }

    // Exponential decay rate (frame-rate independent)
    // Physical meaning: the remaining distance shrinks to e^(-DECAY) per second; DECAY=8 is about 0.03%
    const DECAY = 6;

    let lastTime = performance.now();

    const animateReset = (now) => {
        // Compute the real frame interval (seconds), clamped to prevent jumps after page switches
        const rawDt = (now - lastTime) / 1000;
        const dt = Math.min(rawDt, 0.1);
        lastTime = now;

        // Time-driven exponential decay factor, frame-rate independent
        const alpha = 1 - Math.exp(-DECAY * dt);

        // Target lerp (manipulates xyz directly)
        const t = skinViewer.controls.target;
        const dst = defaultControlsTarget;
        t.x += (dst.x - t.x) * alpha;
        t.y += (dst.y - t.y) * alpha;
        t.z += (dst.z - t.z) * alpha;

        // Current camera offset relative to the target
        const cam = skinViewer.camera.position;
        const ox = cam.x - t.x;
        const oy = cam.y - t.y;
        const oz = cam.z - t.z;

        const dx = defaultCameraPos.x - dst.x;
        const dy = defaultCameraPos.y - dst.y;
        const dz = defaultCameraPos.z - dst.z;

        // Convert to spherical coordinates
        const curR   = Math.sqrt(ox*ox + oy*oy + oz*oz);
        const defR   = Math.sqrt(dx*dx + dy*dy + dz*dz);
        const curPhi = Math.asin(Math.max(-1, Math.min(1, oy / curR)));
        const defPhi = Math.asin(Math.max(-1, Math.min(1, dy / defR)));
        const curTheta = Math.atan2(oz, ox);
        const defTheta = Math.atan2(dz, dx);

        // Take the shortest path for the azimuth angle
        let dTheta = defTheta - curTheta;
        if (dTheta >  Math.PI) dTheta -= 2 * Math.PI;
        if (dTheta < -Math.PI) dTheta += 2 * Math.PI;

        // Interpolate
        const nextR     = curR   + (defR   - curR)   * alpha;
        const nextPhi   = curPhi + (defPhi - curPhi)  * alpha;
        const nextTheta = curTheta + dTheta * alpha;

        // Convert back to Cartesian coordinates
        const cosPhi = Math.cos(nextPhi);
        cam.x = t.x + nextR * cosPhi * Math.cos(nextTheta);
        cam.y = t.y + nextR * Math.sin(nextPhi);
        cam.z = t.z + nextR * cosPhi * Math.sin(nextTheta);

        skinViewer.controls.update();

        // Termination check
        const dpx = cam.x - defaultCameraPos.x;
        const dpy = cam.y - defaultCameraPos.y;
        const dpz = cam.z - defaultCameraPos.z;
        const distPos = Math.sqrt(dpx*dpx + dpy*dpy + dpz*dpz);

        const ttx = t.x - dst.x;
        const tty = t.y - dst.y;
        const ttz = t.z - dst.z;
        const distTarget = Math.sqrt(ttx*ttx + tty*tty + ttz*ttz);

        if (distPos > 0.05 || distTarget > 0.05) {
            resetAnimationId = requestAnimationFrame(animateReset);
        } else {
            cam.x = defaultCameraPos.x;
            cam.y = defaultCameraPos.y;
            cam.z = defaultCameraPos.z;
            t.x = dst.x; t.y = dst.y; t.z = dst.z;
            skinViewer.controls.update();
            resetAnimationId = null;
        }
    };

    // Start the animation (the rAF timestamp shares the same clock as performance.now())
    resetAnimationId = requestAnimationFrame(animateReset);
});

// If the user drags the model while the recenter animation is playing, interrupt it
if (skinViewer.controls) {
    skinViewer.controls.addEventListener("start", () => {
        if (resetAnimationId) {
            cancelAnimationFrame(resetAnimationId);
            resetAnimationId = null;
        }
    });
}

function resize() {
    const w = getWidth();
    const h = getHeight();
    if (w > 0 && h > 0) {
        skinViewer.width = w;
        skinViewer.height = h;
    }
}

window.addEventListener('resize', resize);
setTimeout(resize, 100);
setTimeout(resize, 500);

function loadSkin(skinUrl, model = "auto-detect") {
    skinViewer.loadSkin(skinUrl, { model: model });
}

function loadCape(capeUrl) {
    skinViewer.loadCape(capeUrl);
}