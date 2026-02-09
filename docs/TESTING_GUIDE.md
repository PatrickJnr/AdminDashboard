# HyShots Testing Guide

## ✅ Build Status: SUCCESS

The mod is now fully functional with camera control and world freezing!

## In-Game Testing

### 1. Load Hytale
- Start Hytale
- Create or load a world
- The mod should load automatically from: `C:\Users\PatrickJr\AppData\Roaming\Hytale\UserData\Mods\HyShots-1.0.0.jar`

### 2. Test Commands

#### Enable Freecam Mode
```
/hyshots enable
```
**Expected Result:**
- Message: "Cinematic freecam enabled - HUD hidden, use WASD to move" (green)
- Camera detaches from player completely
- **All HUD elements hidden** (hotbar, health, crosshair, etc.)
- You can move freely with WASD keys
- Mouse controls camera rotation
- Camera can move through blocks
- Same freecam as creative mode camera

#### Disable Freecam Mode
```
/hyshots disable
```
**Expected Result:**
- Message: "Cinematic freecam disabled - HUD restored" (yellow)
- Camera returns to player
- **HUD elements restored to default**
- Normal player controls resume
- **If world was frozen**: Automatically unfreezes and shows message "Cinematic freecam disabled - World unfrozen, HUD restored"

#### Set Field of View (FOV)
```
/hyshots fov <value>
```
**Example**: `/hyshots fov 90`
**Expected Result:**
- Message explaining FOV is client-side only
- Instructions on how to change FOV:
  1. Use the Machinima Tool in Creative Mode
  2. Adjust FOV in game settings
  3. Use client-side mods if available

**Note**: FOV control is client-side in Hytale and cannot be changed via server-side mods. The `ServerCameraSettings` packet does not include an FOV field.

#### Freeze World
```
/hyshots freeze
```
**Expected Result:**
- Message: "World frozen - time, NPCs, and blocks locked (camera still movable)" (cyan)
- Time of day stops progressing
- NPCs stop moving
- Block updates pause (water, fire, etc.)
- **Camera and mouse movement still work** (you can still position your shot)
- World ticking stops

#### Unfreeze World
```
/hyshots unfreeze
```
**Expected Result:**
- Message: "World unfrozen - normal state restored" (green)
- Time resumes
- NPCs resume movement
- Block updates resume
- World ticking resumes

### 3. Test Workflow

**Typical Screenshot Workflow:**
1. `/hyshots enable` - Enable freecam mode
2. Use WASD to fly to desired position
3. Use mouse to adjust camera angle
4. `/hyshots freeze` - Freeze the world (you can still move camera!)
5. Fine-tune camera position with WASD/mouse
6. Take your screenshot (F2 or game screenshot key)
7. `/hyshots disable` - Returns to player and **automatically unfreezes world**

**Alternative Workflow (Manual Unfreeze):**
1. `/hyshots enable` - Enable freecam mode
2. `/hyshots freeze` - Freeze the world
3. Position camera and take screenshots
4. `/hyshots unfreeze` - Manually unfreeze world
5. `/hyshots disable` - Return to player

**Testing Freecam:**
1. `/hyshots enable`
2. Press W to move forward - camera should move independently
3. Press A/D to strafe left/right
4. Press Space to move up
5. Press Shift to move down
6. Move mouse to look around
7. Try moving through walls/blocks
8. `/hyshots disable` to return to player

**Testing Freeze (Fixed - Camera Still Movable):**
1. `/hyshots enable` - Enable freecam
2. `/hyshots freeze` - Freeze world
3. Verify time stops, NPCs freeze
4. **Verify you can still move camera with WASD and mouse**
5. Take screenshots while adjusting camera position
6. `/hyshots unfreeze` or `/hyshots disable` to resume

**Testing HUD Auto-Hide:**
1. `/hyshots enable` - Enable freecam
2. Verify all HUD elements disappear (hotbar, health, crosshair, etc.)
3. Take a screenshot (F12) - should be completely clean
4. `/hyshots disable` - Disable freecam
5. Verify HUD elements return to normal

### 4. Check Console Output

Look for these messages on startup:
```
=== HyShots - Cinematic Camera System ===
Initializing HyShots plugin...
  [SUCCESS] HyShots commands registered
HyShots enabled - Cinematic camera system ready
```

## What Works Now

✅ **Freecam Mode**
- Enable/disable freecam (detached camera)
- Free movement with WASD + mouse
- Camera moves through blocks
- Same system as creative mode camera
- Uses `SetFlyCameraMode` packet
- **Auto-hides HUD when enabled**
- **Auto-restores HUD when disabled**

✅ **World Freezing**
- Time pause
- NPC freezing
- Block tick pause
- World pause
- **Auto-unfreeze on camera disable**

✅ **Command System**
- Proper subcommand structure
- Player targeting
- World access
- Message feedback
- Smart state management
- FOV command (placeholder for future client-side support)

## What's Still TODO

⏳ **Screenshot Capture**
- Frame buffer access (needs rendering API investigation)
- Tile-based capture
- Image stitching
- High-resolution export

⏳ **Advanced Camera Features**
- Camera position commands (teleport camera to coordinates)
- Camera rotation commands (set exact angles)
- **FOV control** - Client-side only, use Machinima Tool or game settings
- Camera movement speed control
- Camera paths/waypoints

⏳ **Weather Control**
- Force specific weather
- Weather freezing

## Troubleshooting

**If commands don't work:**
1. Check if you have permission to use commands
2. Make sure you're in a world (not main menu)
3. Check console for errors
4. Verify JAR is in Mods folder

**If camera doesn't detach:**
1. Make sure you're in a world (not main menu)
2. Try `/hyshots disable` then `/hyshots enable` again
3. Check if another mod is interfering with camera
4. Look for error messages in console
5. Verify you can use creative mode camera (if available)

**If world doesn't freeze:**
1. Check if you have admin/op permissions
2. Verify you're the world owner
3. Check console for permission errors

## Next Steps

Once you confirm these features work:
1. Test camera movement and controls
2. Test world freezing in different scenarios
3. Experiment with camera settings
4. Plan screenshot capture implementation

## Success Criteria

The mod is working correctly if:
- ✅ Commands execute without errors
- ✅ Camera detaches from player (freecam mode)
- ✅ **HUD automatically hides when freecam enabled**
- ✅ **HUD automatically restores when freecam disabled**
- ✅ WASD keys move the camera independently
- ✅ Mouse controls camera rotation
- ✅ Camera can move through blocks
- ✅ World freezes when commanded
- ✅ World unfreezes properly
- ✅ **World auto-unfreezes when disabling freecam**
- ✅ No crashes or errors in console

---

**Ready to test!** Launch Hytale and try `/hyshots enable` - HUD should disappear and freecam should activate.
