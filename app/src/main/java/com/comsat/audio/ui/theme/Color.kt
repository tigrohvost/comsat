package com.comsat.audio.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Cyberpunk palette ────────────────────────────────────────────────────────

val CyanNeon     = Color(0xFF00E5FF)
val CyanDim      = Color(0xFF009BB5)
val MagentaNeon  = Color(0xFFFF00FF)
val MagentaDim   = Color(0xFFAA00AA)
val GreenNeon    = Color(0xFF00FF9F)
val AmberNeon    = Color(0xFFFFAA00)
val RedError     = Color(0xFFFF1744)

// Dark theme
val Background   = Color(0xFF050508)
val Surface      = Color(0xFF0C0C15)
val SurfaceVar   = Color(0xFF131320)
val Outline      = Color(0xFF252540)
val TextPrimary  = Color(0xFFDDE8FF)
val TextSecondary = Color(0xFF6878A8)
val Scrim        = Color(0x99000000)

// Light theme (high-contrast day mode, still cyberpunk)
val BackgroundDay  = Color(0xFFF0F4FF)
val SurfaceDay     = Color(0xFFFFFFFF)
val SurfaceVarDay  = Color(0xFFE4EAFF)
val OutlineDay     = Color(0xFFB8C4D8)
val CyanDay        = Color(0xFF0077A8)
val MagentaDay     = Color(0xFF990099)
// Day-mode tertiary: GreenNeon is unreadable on white; this keeps the hue at
// ~5.5:1 contrast on white, on par with CyanDay.
val GreenDay       = Color(0xFF00784D)
val TextPrimaryDay = Color(0xFF080B1A)
val TextSecDay     = Color(0xFF4A5680)

// ─── Nord · Polar Night ───────────────────────────────────────────────────────
// nordtheme.com — Polar Night (backgrounds) + Snow Storm (text) + Frost/Aurora (accents)
val NordBg        = Color(0xFF2E3440)   // nord0
val NordSurface   = Color(0xFF3B4252)   // nord1
val NordSurfVar   = Color(0xFF434C5E)   // nord2
val NordOutline   = Color(0xFF4C566A)   // nord3
val NordText      = Color(0xFFD8DEE9)   // nord4  — Snow Storm primary text
val NordTextSec   = Color(0xFF81A1C1)   // nord9  — Frost medium blue (secondary text)
val NordPrimary   = Color(0xFF88C0D0)   // nord8  — Frost light blue (primary accent)
val NordSecondary = Color(0xFFB48EAD)   // nord15 — Aurora purple (secondary accent)
val NordTertiary  = Color(0xFFA3BE8C)   // nord14 — Aurora green (status/tertiary)
val NordError     = Color(0xFFBF616A)   // nord11 — Aurora red
val NordYellow    = Color(0xFFEBCB8B)   // nord13 — Aurora yellow (warning/caution)
