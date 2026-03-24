# Design System Specification: Professional Field Operations

## 1. Overview & Creative North Star

### Creative North Star: "The Tactical Monolith"
This design system moves beyond the generic "app" look to create a high-utility, professional tool aesthetic. It is inspired by rugged field hardware and editorial technical manuals. We reject "softness" in favor of **Structural Brutalism**: a philosophy where the layout is defined by heavy-duty data density, high-contrast typography, and a rigid, unyielding grid.

The system is engineered for the high-glare environments of Algerian bus routes. It prioritizes legibility in direct sunlight and "zero-latency" visual processing. By using a mix of Inter’s humanistic clarity and JetBrains Mono’s technical precision, we signal to the conductor that this is not a consumer app, but a precision instrument.

---

## 2. Colors

The color strategy is "Purpose-First." Color is never decorative; it is a status indicator.

### Surface Hierarchy & Nesting
We utilize a "Layered Plate" approach. Instead of traditional shadows, we use background shifts to imply depth.
*   **Base Layer:** `background` (#F0F2F5) acts as the chassis.
*   **Secondary Layer:** `surface-container-low` (#F2F4F7) for grouping related content.
*   **Primary Action Layer:** `surface-container-lowest` (#FFFFFF) for the most interactive elements (cards, inputs).

### The "No-Line" Rule (Refined)
While the technical requirement mentions 1dp borders, as a signature style, we prefer **Tonal Separation**. Use `surface-container-high` (#E6E8EB) against `surface` to define regions. High-contrast borders should be reserved strictly for the "Ghost Border" fallback—using `outline-variant` at 20% opacity—only when two interactive elements of the same color are adjacent.

### Signature Utility Gradients
To avoid a "flat" budget look, we introduce **Functional Luster**. 
*   **Primary Action:** A subtle linear transition from `primary` (#1A56DB) to `primary-container` (#EBF0FF) at a 15-degree angle. This gives a "pressed metal" feel to ticket issuance buttons, making them feel tactile under the thumb.

---

## 3. Typography

The typographic system is the backbone of the "Tactical Monolith." It balances the authoritative weight of Inter with the data-integrity of JetBrains Mono.

*   **Display (Inter Black 800):** Used for critical route numbers or large revenue totals. The -0.5 letter-spacing creates a "block" effect that is readable even through screen glare.
*   **Mono (JetBrains Mono):** Reserved for "Immutable Data"—ticket IDs, currency values, and timestamps. This font switch tells the conductor’s brain: "This information is a system record."
*   **Labels (Inter Medium 500, Uppercase):** Used for metadata headers. The 0.2 letter-spacing ensures that small text doesn't "bleed" together in high-brightness settings.

---

## 4. Elevation & Depth

We replace traditional skeuomorphism with **Tonal Stacking**.

*   **The Layering Principle:** A Conductor's dashboard is a series of "Nested Trays."
    *   `surface` (Main Screen) 
    *   `surface-container-low` (Section Header) 
    *   `surface-container-lowest` (Individual Data Row)
*   **Ambient Shadows:** For floating elements like a "Print Ticket" FAB, use an extra-diffused shadow: `offset-y: 8dp`, `blur: 24dp`, `color: rgba(13, 17, 23, 0.08)`. This mimics natural light without creating "UI clutter."
*   **Tactical Glass:** For modal overlays or "Offline Mode" alerts, use `surface` at 85% opacity with a `16px backdrop-blur`. This maintains the context of the underlying data while bringing the urgent notification to the forefront.

---

## 5. Components

### Buttons: The Physical Trigger
*   **Primary:** High-contrast `primary` fill. Use `XLarge` (24dp) corner radius to differentiate from square data containers.
*   **Secondary:** `surface-container-highest` background with `on-surface` text. For "Draft" or "Hold" actions.
*   **Tertiary:** No background, `primary` text. Use exclusively for "Cancel" or "Back" actions to reduce visual noise.

### Data Cells (Lists)
*   **Constraint:** Forbid divider lines.
*   **Execution:** Separate list items using `12` (2.75rem) vertical spacing or a subtle shift to `surface-container-low` on every second item (zebra-striping) to guide the eye across dense fare tables.

### Input Fields: The Command Line
*   **Style:** `surface-container-lowest` background with a 1dp `outline-variant` (20% opacity).
*   **Active State:** The border thickens to 2dp in `primary` blue. Label moves to `label-sm` above the field in `primary` color.

### Status Chips
*   **Revenue:** `revenue-container` fill with `revenue-green` text. Bold, high-contrast, non-rounded (use `sm` 8dp shape) to maintain the "Rugged" aesthetic.
*   **Sync:** `primary-container` with `sync-blue` text and a rotating Material Symbol.

---

## 6. Do’s and Don’ts

### Do
*   **Do** use `JetBrains Mono` for every single numeral. It ensures digits are distinct (e.g., '0' vs 'O').
*   **Do** leverage `display-lg` for the "Current Fare" to allow conductors to show the screen to passengers for verification from a distance.
*   **Do** use `20` (4.5rem) spacing between unrelated functional groups to prevent accidental taps while the bus is in motion.

### Don’t
*   **Don’t** use shadows to separate cards; use background color shifts.
*   **Don’t** use decorative icons. Every icon must correspond to a direct action or a status state.
*   **Don’t** use the `primary` blue for informational text; reserve it strictly for "Destructive" or "Confirming" actions to maintain its psychological "weight."
*   **Don’t** use pure black for text in light mode; use `ink-primary` (#0D1117) to reduce eye strain over long shifts.

---

## 7. Spacing & Touch Logic

The system utilizes a 4dp base grid but adheres to a **Strict 48dp Minimum Touch Target**. In the rough environment of a moving bus, precision is impossible. Every interactive element—even small toggle switches—must be padded to 48x48dp, even if the visual element is smaller. 

*   **Horizontal Gutter:** `16` (3.5rem) to keep data away from the bezel edge.
*   **Vertical Rhythm:** `8` (1.75rem) for related items; `16` (3.5rem) for new sections.