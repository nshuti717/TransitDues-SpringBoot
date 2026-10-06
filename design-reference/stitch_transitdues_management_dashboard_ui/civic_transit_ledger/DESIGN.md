---
name: Civic Transit Ledger
colors:
  surface: '#f9f9ff'
  surface-dim: '#d1daee'
  surface-bright: '#f9f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f0f3ff'
  surface-container: '#e7eeff'
  surface-container-high: '#dfe8fc'
  surface-container-highest: '#dae3f7'
  on-surface: '#131c2a'
  on-surface-variant: '#434654'
  inverse-surface: '#283140'
  inverse-on-surface: '#ebf1ff'
  outline: '#737685'
  outline-variant: '#c3c6d6'
  surface-tint: '#1555d1'
  primary: '#0650cc'
  on-primary: '#ffffff'
  primary-container: '#356ae6'
  on-primary-container: '#f9f7ff'
  inverse-primary: '#b3c5ff'
  secondary: '#535e7b'
  on-secondary: '#ffffff'
  secondary-container: '#d1dcfe'
  on-secondary-container: '#56607d'
  tertiary: '#00655b'
  on-tertiary: '#ffffff'
  tertiary-container: '#008074'
  on-tertiary-container: '#ddfff8'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dbe1ff'
  primary-fixed-dim: '#b3c5ff'
  on-primary-fixed: '#00174a'
  on-primary-fixed-variant: '#003ea6'
  secondary-fixed: '#d9e2ff'
  secondary-fixed-dim: '#bbc6e7'
  on-secondary-fixed: '#0f1b34'
  on-secondary-fixed-variant: '#3b4662'
  tertiary-fixed: '#8cf5e4'
  tertiary-fixed-dim: '#6fd8c8'
  on-tertiary-fixed: '#00201c'
  on-tertiary-fixed-variant: '#005048'
  background: '#f9f9ff'
  on-background: '#131c2a'
  surface-variant: '#dae3f7'
typography:
  headline-xl:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: 0em
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0em
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0em
  body-sm:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
    letterSpacing: 0em
  label-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0em
  label-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.01em
  label-xs:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.03em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1.5rem
  margin: 2rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system serves municipal-scale transit cooperative operations, where administrative clarity, accountability, and operational trust are paramount. Designed explicitly for cooperative administrators, treasury auditors, and park station managers handling daily moto-taxi dues collections, the interface balances civic utility with approachable modern software engineering standards.

The visual style is **Corporate / Modern** anchored in institutional reliability. It intentionally avoids decorative trends such as heavy glassmorphism, hyper-stylized gradients, or synthetic neon accents. Instead, it prioritizes:
- **Structural Integrity**: High contrast between administrative navigation and operational workspace. A deep navy control rail isolates system context from dynamic cooperative ledger data.
- **Auditability**: Dense, scan-friendly financial records, clear payment reconciliation badges, and disciplined alignment designed for zero-ambiguity daily reviews.
- **Functional Utilitarianism**: Explicit data grids, clearly delineated metric cards, and tactile status indicators that remain instantly legible under variable office lighting conditions.

## Colors

The color architecture is built around operational roles and statutory validation states:

- **Sidebar Navy (`#1F2A44`)**: The dedicated ground for application navigation, contextual workspace switchers, and cooperative identity headers. It serves as an anchor that visually separates system configuration from operational records.
- **Primary Blue (`#356AE6`) & Hover (`#2752B8`)**: Designated solely for primary cooperative actions—recording incoming payments, generating audit exports, and confirming member registrations.
- **Workspace Surfaces**: Main canvas rests on Neutral Light (`#F7F8FA`) with discrete component surfaces resting on pure white (`#FFFFFF`). Boundary definition is enforced by structural border tone (`#E5E7EB`).
- **Semantic Feedback**:
  - **Success Teal/Green (`#2A9D8F`)**: Settled daily dues, cleared bank transfers, and verified license credentials.
  - **Warning Amber (`#E9A23B`)**: Pending mobile money confirmations, grace-period grace balances, and upcoming inspection milestones.
  - **Error Red (`#D9534F`)**: Defaulted dues, invalid badge numbers, and flagged impound notices.
- **Typography Tones**: Primary headers and financial figures utilize charcoal ink (`#27303F`) to prevent eye fatigue while preserving optical sharpness. Supplementary data, timestamps, and column labels utilize muted slate (`#6B7280`).

## Typography

The design system utilizes **Inter** across all typographic applications to ensure universal readability, superior tabular figure alignment, and effortless maintenance.

- **Tabular Numerics**: For dues values, Rwandan Franc currency sums (`RWF`), national ID numbers, and vehicle plate registrations, activate OpenType `tnum` (tabular numbers) to ensure columns align along vertical axes without drift.
- **Hierarchy Structure**:
  - `headline-xl` is reserved for system dashboards and monthly balance summaries.
  - `headline-lg` establishes page titles (e.g., "Kigali Sector 4 Dues Register").
  - `headline-sm` anchors card groupings, modal headers, and table category blocks.
  - `body-md` is the primary interface engine for standard data cells, descriptive notes, and audit logs.
  - `label-xs` drives status pill labels, badge tags, and dense table column headers in uppercase format.

## Layout & Spacing

The target platform is a 1440px fixed-canvas responsive desktop workspace. 

- **Frame Architecture**:
  - **Fixed Primary Navigation Rail**: Width of 260px anchored to the left, occupying full viewport height using `#1F2A44`.
  - **Workspace Canvas**: Fluid remaining area (min-width 1180px at 1440px viewport), bound by a 32px (`margin`) outer screen padding and a structured 12-column grid.
  - **Grid Specifications**: 24px (`gutter`) between interior content cards and modular ledger widgets.
- **Vertical Spacing Cadence**:
  - Component internals strictly adhere to an 8px scale.
  - Form field inputs and simple table rows leverage 8px (`space-sm`) top/bottom padding to preserve compact information density.
  - Metric summary cards use 16px (`space-md`) interior padding.
  - Major workflow divisions, data tables, and tab groups maintain 24px (`space-lg`) to 32px (`space-xl`) vertical rhythm.

## Elevation & Depth

Visual hierarchy uses a disciplined, shallow physical elevation model. High-altitude drops, colored glows, and blurred overlays are strictly excluded to maintain a crisp, business-oriented administrative environment.

- **Level 0 (Flat Canvas)**: The base background (`#F7F8FA`) carries no elevation.
- **Level 1 (Card & Module Default)**: Applied to all standard cards, table shells, and breadcrumb containers:
  - Surface: `#FFFFFF`
  - Border: 1px solid `#E5E7EB`
  - Shadow: `0px 1px 3px rgba(0, 0, 0, 0.05), 0px 1px 2px rgba(0, 0, 0, 0.03)`
- **Level 2 (Interactive Hover & Popovers)**: Applied to hovered cards, dropdown menus, date pickers, and filter expansion sheets:
  - Surface: `#FFFFFF`
  - Border: 1px solid `#E5E7EB`
  - Shadow: `0px 4px 6px -1px rgba(0, 0, 0, 0.07), 0px 2px 4px -1px rgba(0, 0, 0, 0.04)`
- **Level 3 (Modals & Action Drawers)**: Applied to payment entry confirmation dialogs and driver profile side-sheets:
  - Surface: `#FFFFFF`
  - Shadow: `0px 10px 15px -3px rgba(0, 0, 0, 0.08), 0px 4px 6px -2px rgba(0, 0, 0, 0.04)`
  - Backdrop: `rgba(31, 42, 68, 0.40)` (Navy tint)

## Shapes

The design system maintains a consistent **8px (`0.5rem`)** corner radius across standard interactive surfaces. This ensures visual coherence between data inputs, operational cards, and feedback alerts without drifting into overly rounded or toy-like aesthetics.

- **Standard Elements (8px / `rounded-md`)**: Metric cards, data table wrappers, modal containers, text fields, search bars, and standard action buttons.
- **Pill Elements (`rounded-full`)**: Status badges, member registration tags, transaction verification pills, and notification count markers.
- **Micro Elements (4px / `rounded-sm`)**: Table selection checkboxes, nested progress bar fills, and secondary icon-only button states.

## Components

### Buttons
- **Primary**: Solid `#356AE6` fill, `#FFFFFF` text, 8px border radius, 14px font size (`label-md`), vertical padding 10px, horizontal padding 16px. Hover transition to `#2752B8`. Active state compresses slightly with `scale(0.99)`.
- **Secondary**: `#FFFFFF` background, 1px solid `#E5E7EB`, `#27303F` text. Hover transitions to `#F7F8FA` with border tinting to `#D1D5DB`.
- **Destructive**: `#FFFFFF` background with 1px solid `#D9534F`, `#D9534F` text. Hover fills to `#D9534F` with `#FFFFFF` text.

### Data Tables
- **Container**: Elevated Level 1 white surface, 8px corner radius, 1px exterior border (`#E5E7EB`).
- **Header**: `#F7F8FA` background, 40px height, uppercase 11px label font (`label-xs`), color `#6B7280`, 16px horizontal cell padding.
- **Rows**: 52px height, 1px bottom border (`#E5E7EB`). Hover background `#F9FAFB`. Numeric currency values align right; identity badges and driver names align left.

### Status Badges & Pills
- **Structure**: Rounded-full pill, 4px vertical padding, 10px horizontal padding, 12px weight 500 text (`label-sm`).
- **Paid / Cleared**: Background `rgba(42, 157, 143, 0.12)`, text `#2A9D8F`.
- **Pending Verification**: Background `rgba(233, 162, 59, 0.15)`, text `#B8771B`.
- **Overdue / Flagged**: Background `rgba(217, 83, 79, 0.12)`, text `#D9534F`.

### Input Fields & Controls
- **Inputs**: 40px height, 8px border radius, 1px solid `#E5E7EB`, interior padding 12px. Focus state applies a 1px solid `#356AE6` boundary with a soft 3px focus ring of `rgba(53, 106, 230, 0.15)`.
- **Checkboxes**: 18px square, 4px border radius, 1px solid `#D1D5DB`. Selected state fills with `#356AE6` presenting a sharp white check icon.

### Breadcrumbs
- Muted 13px navigation trail (`#6B7280`) separated by subtle slash dividers (`/`). The terminal crumb representing the current view adopts `#27303F` in 500 weight (`label-md`).

### Financial Metric Cards
- White Level 1 surface with 16px padding. Features a 12px uppercase category label, a 24px bold currency display, and a micro baseline indicator tracking weekly collection balance against target quotas.