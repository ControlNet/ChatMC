#!/usr/bin/env python3
"""
Generate AI Terminal textures for ChatMCAe mod.
Creates a rotating wireframe cube animation for the terminal's bright layer,
and a static cube for the off state.

Usage:
    python generate_ai_terminal_textures.py

Output:
    ext-ae/common-1.20.1/src/main/resources/assets/chatmcae/textures/part/
        ai_terminal_on.png        - Animated rotating cube (16 frames)
        ai_terminal_on.png.mcmeta - Animation config
        ai_terminal_off.png       - Static cube for off state (first frame)

Note: The background texture reuses AE2's monitor_light texture (ae2:part/monitor_light)
"""

import numpy as np
from PIL import Image
import math
import os

# Configuration
CUBE_SIZE = 1.3  # Size of the cube (smaller = smaller cube)
TILT_ANGLE = 25  # Degrees to tilt the cube for 3D effect
NUM_FRAMES = 16  # Number of animation frames
FRAME_TIME = 5  # Ticks per frame (20 ticks = 1 second)
SCALE = 2.5  # Projection scale
OFFSET_X = 8  # Center X offset
OFFSET_Y = 8  # Center Y offset
STATIC_ANGLE = (
    0  # Y rotation angle for static cube (degrees) - matches first frame of animation
)


def rotate_y(points, angle):
    """Rotate points around Y axis"""
    cos_a, sin_a = math.cos(angle), math.sin(angle)
    rotation = np.array([[cos_a, 0, sin_a], [0, 1, 0], [-sin_a, 0, cos_a]])
    return points @ rotation.T


def rotate_x(points, angle):
    """Rotate points around X axis"""
    cos_a, sin_a = math.cos(angle), math.sin(angle)
    rotation = np.array([[1, 0, 0], [0, cos_a, -sin_a], [0, sin_a, cos_a]])
    return points @ rotation.T


def project_isometric(points, scale=SCALE, offset_x=OFFSET_X, offset_y=OFFSET_Y):
    """Isometric projection to 2D"""
    projected = []
    for p in points:
        x = p[0] - p[2] * 0.5
        y = -p[1] + p[2] * 0.3

        px = int(x * scale + offset_x)
        py = int(y * scale + offset_y)
        projected.append((px, py, p[2]))
    return projected


def draw_line(img, p1, p2, color):
    """Draw a line using Bresenham's algorithm"""
    x1, y1 = int(p1[0]), int(p1[1])
    x2, y2 = int(p2[0]), int(p2[1])
    dx = abs(x2 - x1)
    dy = abs(y2 - y1)
    sx = 1 if x1 < x2 else -1
    sy = 1 if y1 < y2 else -1
    err = dx - dy

    while True:
        if 0 <= x1 < 16 and 0 <= y1 < 16:
            img.putpixel((x1, y1), color)
        if x1 == x2 and y1 == y2:
            break
        e2 = 2 * err
        if e2 > -dy:
            err -= dy
            x1 += sx
        if e2 < dx:
            err += dx
            y1 += sy


def generate_cube_frames():
    """Generate rotating cube animation frames"""
    # Define cube vertices
    size = CUBE_SIZE
    cube_vertices = np.array(
        [
            [-size, -size, -size],
            [+size, -size, -size],
            [+size, +size, -size],
            [-size, +size, -size],
            [-size, -size, +size],
            [+size, -size, +size],
            [+size, +size, +size],
            [-size, +size, +size],
        ]
    )

    # Define edges (pairs of vertex indices)
    edges = [
        (0, 1),
        (1, 2),
        (2, 3),
        (3, 0),  # Back face
        (4, 5),
        (5, 6),
        (6, 7),
        (7, 4),  # Front face
        (0, 4),
        (1, 5),
        (2, 6),
        (3, 7),  # Connecting edges
    ]

    # Apply initial tilt
    base_vertices = rotate_x(cube_vertices, math.radians(TILT_ANGLE))

    frames = []
    for i in range(NUM_FRAMES):
        angle = (2 * math.pi * i) / NUM_FRAMES
        rotated = rotate_y(base_vertices, angle)
        projected = project_isometric(rotated)

        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

        for e in edges:
            p1 = projected[e[0]]
            p2 = projected[e[1]]
            draw_line(img, p1, p2, (255, 255, 255, 255))

        frames.append(img)

    return frames


def generate_static_cube():
    """Generate a single static cube frame at the perfect viewing angle"""
    # Define cube vertices
    size = CUBE_SIZE
    cube_vertices = np.array(
        [
            [-size, -size, -size],
            [+size, -size, -size],
            [+size, +size, -size],
            [-size, +size, -size],
            [-size, -size, +size],
            [+size, -size, +size],
            [+size, +size, +size],
            [-size, +size, +size],
        ]
    )

    # Define edges (pairs of vertex indices)
    edges = [
        (0, 1),
        (1, 2),
        (2, 3),
        (3, 0),  # Back face
        (4, 5),
        (5, 6),
        (6, 7),
        (7, 4),  # Front face
        (0, 4),
        (1, 5),
        (2, 6),
        (3, 7),  # Connecting edges
    ]

    # Apply tilt and rotation for perfect viewing angle
    tilted = rotate_x(cube_vertices, math.radians(TILT_ANGLE))
    rotated = rotate_y(tilted, math.radians(STATIC_ANGLE))
    projected = project_isometric(rotated)

    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    for e in edges:
        p1 = projected[e[0]]
        p2 = projected[e[1]]
        draw_line(img, p1, p2, (255, 255, 255, 255))

    return img


def main():
    # Determine output directory
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    output_dir = os.path.join(
        project_root,
        "ext-ae/common-1.20.1/src/main/resources/assets/chatmcae/textures/part",
    )

    # Create output directory if needed
    os.makedirs(output_dir, exist_ok=True)

    print(f"Generating AI Terminal textures...")
    print(f"Output directory: {output_dir}")
    print(
        f"Settings: cube_size={CUBE_SIZE}, tilt={TILT_ANGLE}°, frames={NUM_FRAMES}, frametime={FRAME_TIME}"
    )

    # Generate bright layer (animated cube)
    frames = generate_cube_frames()
    total_height = 16 * NUM_FRAMES
    bright_strip = Image.new("RGBA", (16, total_height), (0, 0, 0, 0))
    for i, frame in enumerate(frames):
        bright_strip.paste(frame, (0, i * 16))
    bright_strip.save(os.path.join(output_dir, "ai_terminal_on.png"))
    print(f"  Created ai_terminal_on.png (16x{total_height}, {NUM_FRAMES} frames)")

    # Generate mcmeta for animation
    mcmeta = f"""{{"animation": {{"frametime": {FRAME_TIME}, "interpolate": false}}}}"""
    with open(os.path.join(output_dir, "ai_terminal_on.png.mcmeta"), "w") as f:
        f.write(mcmeta)
    print(f"  Created ai_terminal_on.png.mcmeta (frametime={FRAME_TIME})")

    # Generate static cube for off state
    static_cube = generate_static_cube()
    static_cube.save(os.path.join(output_dir, "ai_terminal_off.png"))
    print(f"  Created ai_terminal_off.png (16x16, angle={STATIC_ANGLE}°)")

    # Calculate animation timing
    rotation_time = (NUM_FRAMES * FRAME_TIME) / 20.0
    print(f"\nAnimation: {rotation_time:.1f} seconds per full rotation")
    print("Done!")


if __name__ == "__main__":
    main()
