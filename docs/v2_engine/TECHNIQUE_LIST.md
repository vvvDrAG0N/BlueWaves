# 🛠️ Titan Technique List: Advanced Rendering & Math

This list defines the specific technical implementations required for the Liquid UI.

## 1. AGSL Shaders (Vulkan/Skia)
*   **Liquid Melt**: A vertex-fragment hybrid that deforms UI coordinates during transitions.
*   **Ocean Caustics**: Perlin-noise based refraction shader for background depth.
*   **Refraction Glass**: A distortion shader for "Theater Bubbles" using screen-space buffers.

## 2. Ray-Tracing (Math Layer)
*   **Dynamic Lighting**: Calculating the `L dot N` (Lambertian) light intensity for 3D card tilt in real-time.
*   **Shadow Casting**: Geometric ray-plane intersection math to project card shadows onto the background.
*   **Specular Highlights**: Simulating "Wet" surfaces on cards using the Blinn-Phong model.

## 3. Path-Tracing (Simulated Logic)
*   **Soft Ambient Occlusion**: Using a multi-sample proximity check to darken corners where cards meet the "Canyon Walls."
*   **Global Illumination (GI)**: Spreading the color of the book cover into the surrounding "Water" (subtle color bleeding).

## 4. Physics & Optimization
*   **Spring Dynamics**: Using `Animatable` with high-frequency stiffness for interaction response.
*   **Native Math (C++)**: Offloading ray-intersection and FFT frequency analysis to the Titan Math Engine.
*   **LOD (Level of Detail)**: Reducing shader complexity for cards that are small or distant in the volumetric grid.
