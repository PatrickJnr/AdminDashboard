# HyShots API Limitations & Discoveries

## Build Status: ✅ SUCCESS

The HyShots mod now compiles and deploys successfully!

## Current Implementation Status

### ✅ Implemented & Working

1. **Plugin Architecture**
   - `HyShotsPlugin` extends `JavaPlugin` with `JavaPluginInit` constructor
   - Proper plugin initialization and setup
   - Command registration system
   - Singleton pattern for plugin access

2. **Camera Management**
   - `CinematicCameraManager` with camera state tracking
   - Camera activation/deactivation per player
   - Camera state data structure (position, rotation, FOV)
   - Uses correct `CameraManager.getComponentType()` pattern

3. **Command System**
   - Main `/hyshots` command registered and working
   - Placeholder subcommands created (awaiting API)
   - Proper `AbstractCommand` usage
   - Message system with color support

4. **Screenshot Architecture**
   - `ScreenshotCaptureSystem` with tile-based capture design
   - `TileRenderer` placeholder for frame capture
   - `ImageStitcher` placeholder for image assembly
   - Path generation for screenshot output

5. **World State Management**
   - `WorldStateManager` architecture in place
   - Freeze/unfreeze tracking per player
   - Weather control placeholder

### ⏳ Awaiting Public API

The following features are designed and ready but cannot be implemented due to missing public API access:

#### 1. World Control APIs (Not in Public Mod API)

**Missing Classes:**
- `com.hypixel.hytale.component.Store`
- `com.hypixel.hytale.component.data.Ref`
- `com.hypixel.hytale.server.core.entity.EntityStore`
- `com.hypixel.hytale.server.core.universe.World`
- `com.hypixel.hytale.server.core.universe.WorldConfig`

**Needed Methods (Discovered in Decompiled Code):**
```java
// World access
Ref<EntityStore> ref = playerRef.getReference();
Store<EntityStore> store = ref.getStore();
World world = store.getExternalData().getWorld();

// Time control
WorldConfig.setGameTimePaused(boolean)
WorldConfig.getGameTime()
WorldConfig.setGameTime(Instant)

// World state
World.setPaused(boolean)
World.setTicking(boolean)
WorldConfig.setBlockTicking(boolean)

// NPC control
WorldConfig.setIsAllNPCFrozen(boolean)
WorldConfig.isAllNPCFrozen()

// Weather control
WorldConfig.setForcedWeather(String)
WorldConfig.getForcedWeather()
```

#### 2. Command System APIs (Not in Public Mod API)

**Missing Classes:**
- `com.hypixel.hytale.server.core.command.system.basecommands.AbstractTargetPlayerCommand`
- `com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand`
- `com.hypixel.hytale.server.core.command.system.AbstractCommandCollection`

**Needed Features:**
- Subcommand registration (`registerSubCommand()`)
- Automatic World and PlayerRef injection
- Command argument types (`RequiredArg<T>`, `OptionalArg<T>`)
- `ArgTypes.DOUBLE`, `ArgTypes.PLAYER_REF`, etc.

#### 3. Rendering APIs (Not Available)

**Missing Capabilities:**
- Frame buffer access
- Projection matrix manipulation
- Render target control
- Pixel data extraction
- Image encoding (PNG, EXR)

### 📋 Implementation Roadmap

When APIs become available, implement in this order:

1. **Phase 1: World Control** (When World/WorldConfig APIs available)
   - Update `WorldStateManager.freezeWorld()` with real implementation
   - Add time locking
   - Add weather control
   - Add NPC freezing
   - Add block tick control

2. **Phase 2: Command Structure** (When AbstractTargetPlayerCommand available)
   - Convert commands to use `AbstractTargetPlayerCommand`
   - Add subcommand registration
   - Add command arguments (position, rotation, FOV)
   - Implement camera control commands

3. **Phase 3: Rendering** (When rendering APIs available)
   - Implement `TileRenderer.captureTiles()`
   - Add projection matrix offset calculation
   - Implement frame buffer capture
   - Add image encoding
   - Implement `ImageStitcher.stitchTiles()`

## Discovered API Patterns (From Decompiled Code)

### World Access Pattern
```java
Ref<EntityStore> ref = playerRef.getReference();
if (ref != null && ref.isValid()) {
    Store<EntityStore> store = ref.getStore();
    World world = store.getExternalData().getWorld();
    WorldConfig config = world.getWorldConfig();
}
```

### Component Access Pattern
```java
CameraManager cameraManager = store.getComponent(ref, CameraManager.getComponentType());
TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
Player player = store.getComponent(ref, Player.getComponentType());
```

### Command Pattern
```java
public class MyCommand extends AbstractTargetPlayerCommand {
    @Override
    protected void execute(
            CommandContext context,
            Ref<EntityStore> sourceRef,
            Ref<EntityStore> targetRef,
            PlayerRef playerRef,
            World world,
            Store<EntityStore> store) {
        // World and player automatically provided!
    }
}
```

## Files Ready for API Integration

When APIs become available, update these files:

1. `WorldStateManager.java` - Lines 29-56 (freezeWorld method)
2. `WorldStateManager.java` - Lines 61-82 (unfreezeWorld method)
3. `CameraEnableCommand.java` - Convert to AbstractTargetPlayerCommand
4. `CameraDisableCommand.java` - Convert to AbstractTargetPlayerCommand
5. `FreezeWorldCommand.java` - Convert to AbstractTargetPlayerCommand
6. `UnfreezeWorldCommand.java` - Convert to AbstractTargetPlayerCommand
7. `HyShotsCommand.java` - Add subcommand registration
8. `TileRenderer.java` - Implement frame capture
9. `ImageStitcher.java` - Implement image assembly

## Current Capabilities

What the mod CAN do right now:
- ✅ Load successfully in Hytale
- ✅ Register `/hyshots` command
- ✅ Track camera states per player
- ✅ Show status messages
- ✅ Demonstrate proper plugin architecture

What the mod CANNOT do yet:
- ❌ Actually control the camera (no camera control API)
- ❌ Freeze world state (no World/WorldConfig API)
- ❌ Capture screenshots (no rendering API)
- ❌ Use subcommands (no AbstractCommandCollection API)

## Conclusion

The HyShots mod has a complete, well-architected foundation that compiles and runs. All core systems are designed and ready. Implementation is blocked only by missing public API access to:

1. World control (World, WorldConfig classes)
2. Advanced command system (AbstractTargetPlayerCommand, subcommands)
3. Rendering system (frame buffers, image capture)

Once Hypixel exposes these APIs to the public mod API, the implementation can proceed rapidly as all architecture is already in place.

---

**Last Updated**: February 7, 2026  
**Build Status**: ✅ Compiling and deploying successfully  
**Deployment Path**: `C:\Users\PatrickJr\AppData\Roaming\Hytale\UserData\Mods\HyShots-1.0.0.jar`
