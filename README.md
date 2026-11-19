# BR-AimAssist

BR-AimAssist is a client-side Fabric mod for Minecraft Java 26.3. Hold **R** (rebindable in Controls) to strongly guide the view toward the mob or player nearest the crosshair. A target must be visible, within 16 blocks, and inside a 45-degree cone around the crosshair to acquire. Aim is directed at the closest point on the target's hitbox. Once acquired, it stays locked and tracked even if it moves out of range or line of sight; release **R** to unlock. The bottom-left HUD shows a locked player's name or a mob's type.

## Build

Use Java 25 and run `./gradlew build`. The finished mod jar is written to `build/libs/`.
