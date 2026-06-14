
# Ghost System Implementation TODO

## Phase 1: Plugin-side (WolfPlugin)
- [ ] Create GhostFrame.java (data model: x, y, z, yaw, pitch, tick)
- [ ] Create GhostRecorder.java (tick-based recording, ArrayList append)
- [ ] Create GhostManager.java (start/stop/save/load logic)
- [ ] Create GhostStorage.java (async JSON read/write in plugin folder)
- [ ] Update WolfPlugin.java (register ghost commands, integrate recorder)
- [ ] Update VarIntUtils.java (add binary encoding for ghost frames)

## Phase 2: Mod-side (WolfMOD)
- [ ] Create GhostDataPayload.java (custom payload for ghost binary data)
- [ ] Create GhostRenderer.java (render semi-transparent boat + player)
- [ ] Create GhostData.java (client-side storage and interpolation)
- [ ] Update WolfmodClient.java (register ghost packet receiver, render hook)

## Phase 3: Communication
- [ ] Plugin sends ghost_start / ghost_stop keys via existing channel
- [ ] Plugin sends ghost_data binary blob to client
- [ ] Client receives and stores frames for rendering

## Optimization Goals
- Zero MSPT: Only ArrayList.add() per tick
- Memory cleanup: Clear map entry immediately after async save
- JSON storage: Single file per player+track, async I/O
- Rendering: Interpolated position, semi-transparent boat+player

