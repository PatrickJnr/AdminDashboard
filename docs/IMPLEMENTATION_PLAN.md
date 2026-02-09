# HyShots Implementation Plan - UPDATED

## CRITICAL DISCOVERY

The APIs we need ARE AVAILABLE! Looking at MapGen and the decompiled camera commands, we can see:

### ✅ Available APIs

1. **AbstractCommandCollection** - For command hierarchies
   ```java
   public class HyShotsCommand extends AbstractCommandCollection {
       public HyShotsCommand() {
           super("hyshots", "description");
           this.addSubCommand(new CameraEnableCommand());  // THIS WORKS!
       }
   }
   ```

2. **AbstractTargetPlayerCommand** - For player-targeted commands
   ```java
   public class CameraEnableCommand extends AbstractTargetPlayerCommand {
       @Override
       protected void execute(CommandContext context, Ref<EntityStore> sourceRef, 
                            Ref<EntityStore> ref, PlayerRef playerRef, 
                            World world, Store<EntityStore> store) {
           // World and PlayerRef automatically provided!
       }
   }
   ```

3. **Camera Control via Packets**
   ```java
   // Reset camera
   playerRef.getPacketHandler().writeNoCache(
       new SetServerCamera(ClientCameraView.Custom, false, null)
   );
   
   // Set custom camera
   ServerCameraSettings settings = new ServerCameraSettings();
   settings.distance = 20.0f;
   settings.displayCursor = true;
   // ... configure settings
   playerRef.getPacketHandler().writeNoCache(
       new SetServerCamera(ClientCameraView.Custom, true, settings)
   );
   ```

4. **World Access**
   ```java
   World world = store.getExternalData().getWorld();
   WorldConfig config = world.getWorldConfig();
   ```

## Implementation Steps

### Phase 1: Fix Command Structure ✅ READY TO IMPLEMENT

1. Change `HyShotsCommand` from `AbstractCommand` to `AbstractCommandCollection`
2. Use `addSubCommand()` instead of `registerSubCommand()`
3. Convert all subcommands to use `AbstractTargetPlayerCommand`
4. Add proper imports for all the classes

### Phase 2: Implement Camera Control

1. Create `ServerCameraSettings` configurations
2. Use `SetServerCamera` packet to control camera
3. Implement camera modes:
   - Freecam (detached from player)
   - Fixed position
   - Orbit mode
   - Path following

### Phase 3: World State Management

1. Use `World` and `WorldConfig` APIs
2. Implement time freezing
3. Implement weather control
4. Implement NPC freezing

### Phase 4: Screenshot Capture

Still needs investigation for:
- Frame buffer access
- Render target manipulation
- Image encoding

## Next Actions

1. Update all imports to use correct package paths
2. Change command base classes
3. Implement camera packet system
4. Test in-game

The mod CAN be fully functional for camera control and world freezing!
