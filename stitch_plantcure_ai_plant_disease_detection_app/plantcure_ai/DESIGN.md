---
name: PlantCure AI
colors:
  surface: '#f7fbf2'
  surface-dim: '#d7dbd3'
  surface-bright: '#f7fbf2'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f1f5ec'
  surface-container: '#ebefe6'
  surface-container-high: '#e5e9e1'
  surface-container-highest: '#e0e4db'
  on-surface: '#181d18'
  on-surface-variant: '#40493f'
  inverse-surface: '#2d322c'
  inverse-on-surface: '#eef2e9'
  outline: '#707a6e'
  outline-variant: '#bfc9bc'
  surface-tint: '#1d6c30'
  primary: '#00511d'
  on-primary: '#ffffff'
  primary-container: '#1b6b2f'
  on-primary-container: '#99e99e'
  inverse-primary: '#89d88f'
  secondary: '#964900'
  on-secondary: '#ffffff'
  secondary-container: '#fe851f'
  on-secondary-container: '#612d00'
  tertiary: '#155025'
  on-tertiary: '#ffffff'
  tertiary-container: '#30693a'
  on-tertiary-container: '#a8e6ab'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#a4f5a9'
  primary-fixed-dim: '#89d88f'
  on-primary-fixed: '#002107'
  on-primary-fixed-variant: '#00531d'
  secondary-fixed: '#ffdcc7'
  secondary-fixed-dim: '#ffb787'
  on-secondary-fixed: '#311300'
  on-secondary-fixed-variant: '#723600'
  tertiary-fixed: '#b3f2b6'
  tertiary-fixed-dim: '#98d59c'
  on-tertiary-fixed: '#002108'
  on-tertiary-fixed-variant: '#175125'
  background: '#f7fbf2'
  on-background: '#181d18'
  surface-variant: '#e0e4db'
typography:
  headline-lg:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
  headline-md:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 22px
    fontWeight: '700'
    lineHeight: 28px
  title-lg:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 26px
  body-lg:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 26px
  body-md:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-lg:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.1px
  headline-lg-mobile:
    fontFamily: Atkinson Hyperlegible Next
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 8px
  xs: 4px
  sm: 12px
  md: 16px
  lg: 24px
  xl: 32px
  touch-target: 48px
  container-margin: 16px
  gutter: 16px
---

## Brand & Style

The design system is built on a foundation of **Utility, Trust, and Accessibility**. Designed specifically for rural Indian farmers, the aesthetic prioritizes immediate comprehension and ease of use in high-glare outdoor environments. 

The style is a **Modern-Tactile** evolution of Material Design 3. It utilizes a high-contrast palette to ensure legibility and "chunky" interactive elements that accommodate diverse motor skills and environmental conditions. The brand personality is that of a reliable digital companion—authoritative yet approachable, helping users safeguard their livelihood through technology.

Key visual principles:
- **Clarity over Decoration:** Every element serves a functional purpose.
- **High Visual Information Density:** Reducing the need for deep navigation.
- **Organic Reliability:** Combining deep earth tones with vibrant status indicators.

## Colors

The color palette is optimized for high-contrast visibility. 

- **Primary (#1B6B2F):** A deep, forest green used for key actions and brand presence. It evokes growth and health.
- **Accent (#F57F17):** A high-visibility orange used exclusively for alerts, "Action Required" states, and disease detection highlights. 
- **Toolbar (#0D4A1F):** A darker shade of the primary green used for the top app bar to provide a strong visual anchor and reduce glare at the top of the device.
- **Background (#FAFAFA):** A near-white neutral that minimizes eye strain while maintaining a clean, modern feel.
- **Surface:** Pure white is used for cards and interactive components to pop against the light grey background.

## Typography

This design system utilizes **Atkinson Hyperlegible Next** for all text. This typeface was specifically chosen for its focus on character differentiation, which is critical for users with varying levels of literacy or visual impairment.

- **Legibility First:** High x-heights and distinct letterforms (e.g., distinguishing 'I', 'l', and '1') ensure information is transmitted accurately.
- **Scale:** Body text starts at a minimum of 16px to accommodate outdoor usage where glare might reduce contrast.
- **Weights:** Heavy use of Semi-Bold and Bold weights for headlines to create a clear information hierarchy at a glance.

## Layout & Spacing

The layout utilizes a **Fluid Grid** system optimized for mobile-first interactions. 

- **Grid:** A 4-column grid for mobile and an 8-column grid for tablets. 
- **Margins:** 16px horizontal margins provide a consistent frame for content.
- **Rhythm:** An 8px linear scale governs all vertical spacing. 
- **Touch Targets:** A strict minimum touch target of 48x48px is enforced for all interactive elements, though 56px is preferred for primary agricultural workflows (like "Scan Plant").

## Elevation & Depth

Visual hierarchy is established using **Tonal Layers** and **Soft Ambient Shadows**.

- **Level 0 (Background):** #FAFAFA.
- **Level 1 (Cards/Surface):** Pure white with a subtle 1px border (#E0E0E0) and a soft, low-opacity shadow (4px blur, 10% opacity black).
- **Level 2 (Active/Pressed States):** Increased shadow depth (8px blur, 15% opacity) and a slight tint of the Primary color.
- **Toolbars:** Elevated at Level 2 to remain persistent above scrolling content.

This system avoids heavy skeuomorphism but uses depth to indicate that cards are "liftable" and buttons are "pressable."

## Shapes

The shape language is **Friendly and Accessible**. 

- **Standard Elements:** 0.5rem (8px) corner radius for buttons and small components.
- **Cards:** 1rem (16px) corner radius to give them a distinct, approachable feel that stands out from the screen edges.
- **Large Containers:** 1.5rem (24px) for bottom sheets and top-level dashboard containers.

The consistent use of rounded corners softens the high-contrast color palette, making the app feel helpful rather than clinical.

## Components

### Buttons
- **Primary:** Filled with #1B6B2F, White text, 56px height.
- **Secondary:** Outlined with #1B6B2F, 2px stroke, 48px height.
- **FAB (Floating Action Button):** Large (96x96px) circular button for the "Camera/Scan" action, using the Accent #F57F17 color.

### Cards
- White background with 16px padding and 16px corner radius.
- Cards must have a clear headline (Title-LG) and use high-contrast icons for status (e.g., a large green checkmark for "Healthy").

### Input Fields
- Filled style with a thick (2px) bottom indicator.
- Labels are persistent; placeholders are never used in place of labels to ensure the user always knows what data is being requested.

### Iconography
- Use **Thick-stroke (2pt minimum)** icons.
- Avoid fine-line details. 
- Icons should be paired with text labels whenever possible to assist users who may not be familiar with standard digital iconography.

### Status Chips
- **Healthy:** Light green background with Dark Green text.
- **At Risk:** Light orange background with Dark Orange text.
- **Diseased:** Light red background with Dark Red text.