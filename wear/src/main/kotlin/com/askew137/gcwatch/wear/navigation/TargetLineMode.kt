package com.askew137.gcwatch.wear.navigation

/** What the map draws between the user and the active target. */
enum class TargetLineMode {
    /** Straight dashed line (default when a new target arrives). */
    AIR_LINE,

    /** The route received from the phone (only offered when one exists). */
    ROUTE,

    /** Only the target marker, no line. */
    OFF,
}
