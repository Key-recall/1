This is a Fabric client mod that shows the name of the item held by the
invisible item frame (or the fake block it is disguised as) under your
crosshair.

## What it does

When you aim your crosshair at a "3D item display" (which is actually an
invisible ItemFrameEntity disguised by the server as an iron trapdoor on the
floor, or a dead tube coral fan on the wall), this mod reads the item stack
inside that item frame and draws its display name in a small label near the
center of the screen.

## How it works

1. Reads the client crosshair hit result.
2. If the hit entity is an `ItemFrameEntity`, reads `getHeldItemStack()`.
3. If the hit is a block (the fake trapdoor / coral fan), searches for the
   nearest item frame within a small radius.
4. As a fallback, raycasts along the player's view direction for a nearby
   item frame.
5. Draws the item's name on the HUD.

## Building

```
./gradlew build
```

The jar will be in `build/libs/`.

## Installing

Put the built jar into your client's `mods` folder (requires Fabric Loader
0.20.1+ and Fabric API for Minecraft 1.21.11).
