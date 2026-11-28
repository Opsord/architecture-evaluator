import { CanvasTexture, RepeatWrapping, SRGBColorSpace } from "three";
import type { JavaFileType } from "../../../../../types/class/JavaFileType.ts";

const TEXTURE_SIZE = 256;
const BASE_COLOR = "#ffffff";
const PATTERN_COLOR = "#262626";

const textureCache = new Map<JavaFileType, CanvasTexture>();

function createCanvasTexture(
    draw: (ctx: CanvasRenderingContext2D, size: number) => void,
    repeatX = 1,
    repeatY = 1,
): CanvasTexture | null {
    if (typeof document === "undefined") {
        return null;
    }
    const canvas = document.createElement("canvas");
    canvas.width = TEXTURE_SIZE;
    canvas.height = TEXTURE_SIZE;
    const ctx = canvas.getContext("2d");
    if (!ctx) {
        return null;
    }

    ctx.fillStyle = BASE_COLOR;
    ctx.fillRect(0, 0, TEXTURE_SIZE, TEXTURE_SIZE);

    draw(ctx, TEXTURE_SIZE);

    const texture = new CanvasTexture(canvas);
    texture.colorSpace = SRGBColorSpace;
    texture.wrapS = RepeatWrapping;
    texture.wrapT = RepeatWrapping;
    texture.repeat.set(repeatX, repeatY);
    texture.anisotropy = 4;
    return texture;
}

function drawInterfaceStripes(ctx: CanvasRenderingContext2D, size: number): void {
    const step = 64;
    ctx.strokeStyle = PATTERN_COLOR;
    ctx.lineWidth = step / (2 * Math.SQRT2);
    ctx.beginPath();
    // Draw bold "/" diagonal stripes matching CSS repeating-linear-gradient(-45deg, ...)
    for (let x = -size; x <= size * 2; x += step) {
        ctx.moveTo(x, size);
        ctx.lineTo(x + size, 0);
    }
    ctx.stroke();
}

function drawAbstractDots(ctx: CanvasRenderingContext2D, size: number): void {
    const step = 64;
    const radius = 15;
    ctx.fillStyle = PATTERN_COLOR;
    for (let y = step / 2; y < size; y += step) {
        for (let x = step / 2; x < size; x += step) {
            ctx.beginPath();
            ctx.arc(x, y, radius, 0, Math.PI * 2);
            ctx.fill();
        }
    }
}

function drawRecordFrame(ctx: CanvasRenderingContext2D, size: number): void {
    // Beveled / framed data-card border around each face
    ctx.strokeStyle = PATTERN_COLOR;
    ctx.lineWidth = 24;
    ctx.strokeRect(12, 12, size - 24, size - 24);

    ctx.lineWidth = 8;
    ctx.strokeRect(46, 46, size - 92, size - 92);
}

function drawEnumBands(ctx: CanvasRenderingContext2D, size: number): void {
    // Stacked horizontal bands evoking a discrete list of enum constants
    const bandCount = 4;
    const bandHeight = size / (bandCount * 2);
    ctx.fillStyle = PATTERN_COLOR;
    for (let i = 0; i < bandCount; i++) {
        ctx.fillRect(0, i * bandHeight * 2 + bandHeight * 0.5, size, bandHeight);
    }
}

function drawExceptionChevron(ctx: CanvasRenderingContext2D, size: number): void {
    const step = 64;
    const half = size / 2;
    ctx.strokeStyle = PATTERN_COLOR;
    ctx.lineWidth = 20;
    ctx.lineJoin = "miter";
    ctx.beginPath();
    for (let y = -step; y <= size + step; y += step) {
        ctx.moveTo(-16, y + step * 0.45);
        ctx.lineTo(half, y - step * 0.15);
        ctx.lineTo(size + 16, y + step * 0.45);
    }
    ctx.stroke();
}

function drawAnnotationAt(ctx: CanvasRenderingContext2D, size: number): void {
    ctx.strokeStyle = PATTERN_COLOR;
    ctx.lineWidth = 12;
    ctx.strokeRect(18, 18, size - 36, size - 36);

    ctx.fillStyle = PATTERN_COLOR;
    ctx.font = "bold 152px sans-serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText("@", size / 2, size / 2 + 8);
}

export function getFileTypeTexture(fileType?: JavaFileType | string): CanvasTexture | null {
    if (!fileType || fileType === "CLASS" || fileType === "UNKNOWN") {
        return null;
    }

    const typed = fileType as JavaFileType;
    const cached = textureCache.get(typed);
    if (cached) {
        return cached;
    }

    let created: CanvasTexture | null = null;
    switch (typed) {
        case "INTERFACE":
            created = createCanvasTexture(drawInterfaceStripes, 1, 1);
            break;
        case "ABSTRACT_CLASS":
            created = createCanvasTexture(drawAbstractDots, 1, 1);
            break;
        case "RECORD":
            created = createCanvasTexture(drawRecordFrame, 1, 1);
            break;
        case "ENUM":
            created = createCanvasTexture(drawEnumBands, 1, 1);
            break;
        case "EXCEPTION":
            created = createCanvasTexture(drawExceptionChevron, 1, 1);
            break;
        case "ANNOTATION":
            created = createCanvasTexture(drawAnnotationAt, 1, 1);
            break;
        default:
            return null;
    }

    if (created) {
        textureCache.set(typed, created);
    }
    return created;
}
