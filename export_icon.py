#!/usr/bin/env python3.11
"""
Xuất icon launcher RA dạng PNG 512x512, hình vuông 100% không bo tròn.

Background: #2BB0ED (full square)
Foreground:
  - Thanh ngang màu #0c7cd5, bo đầu pill shape, căn giữa phía dưới
  - Ngôi sao 4 cánh màu #FFEB3B, căn giữa phía trên
"""

import math
from PIL import Image, ImageDraw

SIZE = 512
VIEWPORT = 192  # Android vector viewport
SCALE = SIZE / VIEWPORT  # = 512/192 ≈ 2.6667

def vp(x, y=None):
    """Chuyển tọa độ viewport 192 → pixel 512."""
    if y is None:
        return x * SCALE
    return x * SCALE, y * SCALE


def draw_star_4pt(draw, cx, cy, outer_r, inner_r, color):
    """Vẽ ngôi sao 4 cánh (lemniscate star) bằng polygon Bézier xấp xỉ."""
    # Dùng star path từ XML:
    # M12,0 C12,0 14,8 16,10 C18,12 26,14 26,14
    # C26,14 18,16 16,18 C14,20 12,28 12,28
    # C12,28 10,20 8,18 C6,16 -2,14 -2,14
    # C-2,14 6,12 8,10 C10,8 12,0 12,0 Z
    # Local coords centered at (12,14), range x: -2..26, y: 0..28
    # Group transform: translateX=67, translateY=47, scaleX=2.4, scaleY=2.4

    tx, ty = 67, 47
    sx, sy = 2.4, 2.4

    def transform(lx, ly):
        wx = tx + lx * sx
        wy = ty + ly * sy
        return vp(wx, wy)

    # Approximation: flatten the cubic beziers into polyline points
    def cubic_bezier_points(p0, p1, p2, p3, n=20):
        pts = []
        for i in range(n + 1):
            t = i / n
            x = ((1-t)**3 * p0[0] + 3*(1-t)**2*t * p1[0] +
                 3*(1-t)*t**2 * p2[0] + t**3 * p3[0])
            y = ((1-t)**3 * p0[1] + 3*(1-t)**2*t * p1[1] +
                 3*(1-t)*t**2 * p2[1] + t**3 * p3[1])
            pts.append(transform(x, y))
        return pts

    # Path segments (local coords):
    # M 12,0
    # C 12,0  14,8  16,10
    # C 18,12  26,14  26,14
    # C 26,14  18,16  16,18
    # C 14,20  12,28  12,28
    # C 12,28  10,20  8,18
    # C 6,16  -2,14  -2,14
    # C -2,14  6,12  8,10
    # C 10,8  12,0  12,0
    segments = [
        ((12,0), (12,0), (14,8), (16,10)),
        ((16,10), (18,12), (26,14), (26,14)),
        ((26,14), (26,14), (18,16), (16,18)),
        ((16,18), (14,20), (12,28), (12,28)),
        ((12,28), (12,28), (10,20), (8,18)),
        ((8,18), (6,16), (-2,14), (-2,14)),
        ((-2,14), (-2,14), (6,12), (8,10)),
        ((8,10), (10,8), (12,0), (12,0)),
    ]

    all_pts = []
    for seg in segments:
        pts = cubic_bezier_points(seg[0], seg[1], seg[2], seg[3], n=30)
        if all_pts:
            all_pts.extend(pts[1:])
        else:
            all_pts.extend(pts)

    draw.polygon(all_pts, fill=color)


def draw_pill_rect(draw, x0, y0, x1, y1, radius, color):
    """Vẽ hình chữ nhật bo đầu (pill/rounded rect)."""
    draw.rectangle([x0, y0, x1, y1], fill=color)
    # Bo đầu trái
    draw.ellipse([x0 - radius, y0, x0 + radius, y1], fill=color)
    # Bo đầu phải
    draw.ellipse([x1 - radius, y0, x1 + radius, y1], fill=color)


def main():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # --- Background: hình vuông đặc, không bo tròn ---
    draw.rectangle([0, 0, SIZE - 1, SIZE - 1], fill="#2BB0ED")

    # --- Foreground: thanh ngang ---
    # pathData: M73.5,120.5 L118.5,120.5 A7.5,7.5 ... L73.5,135.5 A7.5,7.5 ...
    bar_x0, bar_y0 = vp(73.5, 120.5)
    bar_x1, bar_y1 = vp(118.5, 135.5)
    bar_r = vp(7.5)
    draw_pill_rect(draw, bar_x0, bar_y0, bar_x1, bar_y1, bar_r, "#0c7cd5")

    # --- Foreground: ngôi sao 4 cánh ---
    draw_star_4pt(draw, 0, 0, 0, 0, "#FFEB3B")

    # Lưu file
    out_path = "/Users/quanlm22/Desktop/Learning/text_actions/ic_launcher_ra_512.png"
    img.save(out_path, "PNG")
    print(f"Saved: {out_path}")
    print(f"Size: {img.size}")
    print(f"Mode: {img.mode}")


if __name__ == "__main__":
    main()
