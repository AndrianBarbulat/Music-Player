package com.example.musicplayerdeck.ui.theme

import androidx.compose.ui.graphics.Color

// ── Backgrounds (darkest → lightest) ──────────────────────────────────────
val AppBackground    = Color(0xFF090C10)  // main app background, deepest layer
val AppSurface       = Color(0xFF0F1318)  // content areas, lists
val AppCard          = Color(0xFF161C24)  // cards, bottom sheets, elevated surfaces
val AppElevated      = Color(0xFF1E2733)  // album art placeholders, input fields, pressed states

// ── Accent ────────────────────────────────────────────────────────────────
val TealPrimary      = Color(0xFF7BA59A)  // active states, highlights, playing indicator
val TealPrimaryDark  = Color(0xFF5D8A7E)  // active song artist text, subtle accents
val TealPrimaryFaint = Color(0x1F7BA59A)  // 12% opacity — tinted bg for active/selected rows

// ── Text ──────────────────────────────────────────────────────────────────
val TextPrimary      = Color(0xFFE8ECF0)  // song titles, headings
val TextSecondary    = Color(0xFF8A96A6)  // artist names, labels, metadata
val TextMuted        = Color(0xFF5A6678)  // timestamps, hints, inactive tabs, disabled
val TextFaint        = Color(0xFF4A5568)  // duration, tertiary info

// ── Borders & dividers ────────────────────────────────────────────────────
val DividerColor      = Color(0xFF1A2030)  // subtle horizontal dividers between rows
val DividerLightColor = Color(0xFF2A3444)  // section separators
