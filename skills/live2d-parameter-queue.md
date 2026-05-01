# Live2D Parameter Queue Pattern

## When to use

- Live2D model parameters (mouth, head, body) jitter or drift unexpectedly during animation playback
- Parameters set from Java/Kotlin layer don't persist correctly between frames
- Breath/Physics effects interfere with manually set parameters
- User reports "stiff" or "unnatural" movement when certain animations play

## The Problem

Live2D SDK has a specific parameter update flow:

```
Update():
  ① LoadParameters()     // Restore saved "base state"
  ② UpdateMotion()       // Apply motion data
  ③ SaveParameters()     // Save base state
  ④ OnLateUpdate()       // Breath/Physics add temporary offsets (not saved)
  ⑤ Render()
```

When Java layer sets parameters via JNI at the wrong time:

```
Frame N:
  Java: SetParameterValue(MouthOpenY=0.8)
        SaveParameters()  // Saves current state INCLUDING Breath offset on AngleX!
        
  OnLateUpdate():         // AngleX = 0 + 5 (Breath offset)
  
Frame N+1:
  LoadParameters()        // AngleX restored as 5 (polluted!)
  OnLateUpdate():         // AngleX = 5 + 5 = 10 (keeps drifting!)
```

The Breath offset gets incorrectly saved as the new "base state", causing parameters to drift.

## The Solution: Parameter Queue

Instead of setting parameters immediately, queue them for execution at the right time.

### C++ Implementation

**1. Add queue data structure (LAppModel.hpp):**

```cpp
struct PendingParameterData
{
    const Csm::CubismId* ParameterId;
    Csm::csmFloat32 Value;
    Csm::csmFloat32 Weight;
};

// ... in class LAppModel
Csm::csmVector<PendingParameterData> _pendingParameters;
void FlushPendingParameters();
```

**2. Queue parameters instead of setting immediately (LAppModel.cpp):**

```cpp
void LAppModel::SetParameterValue(const csmChar* parameterId, csmFloat32 value, csmFloat32 weight)
{
    if (_model == NULL || parameterId == NULL) return;
    
    const CubismIdHandle id = CubismFramework::GetIdManager()->GetId(parameterId);
    
    // Queue instead of direct set
    PendingParameterData data;
    data.ParameterId = id;
    data.Value = value;
    data.Weight = weight;
    _pendingParameters.PushBack(data);
}

void LAppModel::FlushPendingParameters()
{
    for (csmUint32 i = 0; i < _pendingParameters.GetSize(); ++i)
    {
        PendingParameterData* data = &_pendingParameters[i];
        _model->SetParameterValue(data->ParameterId, data->Value, data->Weight);
    }
    _pendingParameters.Clear();
}
```

**3. Execute queue in Update() before SaveParameters():**

```cpp
void LAppModel::Update()
{
    _model->LoadParameters();
    
    if (!_motionManager->IsFinished())
    {
        _motionManager->UpdateMotion(_model, deltaTimeSeconds);
    }
    
    // Execute queued parameters HERE - after Load, before Save
    FlushPendingParameters();
    
    _model->SaveParameters();  // Now saves Java parameters correctly
    
    _updateScheduler.OnLateUpdate(_model, deltaTimeSeconds);  // Breath adds offset AFTER save
    
    _model->Update();
}
```

### Correct Flow After Fix

```
Frame N:
  Java: SetParameterValue() → queued to _pendingParameters

Frame N+1:
  LoadParameters()           // Restore base state
  UpdateMotion()             // Apply motions
  FlushPendingParameters()   // Apply queued params
  SaveParameters()           // Save with Java params, without Breath offset
  OnLateUpdate()             // Breath adds offset (not saved)
  Render()
```

## Key Insights

1. **Never call SaveParameters() from JNI callbacks** - It will capture Breath/Physics temporary offsets
2. **Parameters must be set between LoadParameters and SaveParameters** - Or they'll be overwritten
3. **Breath uses AddParameterValue, not SetParameterValue** - It's designed to add on top of base state
4. **GLSurfaceView.queueEvent executes after onDrawFrame** - Timing matters!

## Diagnostic Checklist

- [ ] Check if `SaveParameters()` is called from JNI
- [ ] Verify parameter timing relative to `LoadParameters()/SaveParameters()`
- [ ] Check if Breath/Physics parameters are drifting frame-over-frame
- [ ] Verify queue is cleared after each flush to prevent memory growth

## Related Files

- `LAppModel.hpp` - Queue structure definition
- `LAppModel.cpp` - SetParameterValue() and Update() implementation
- `JniBridgeC.cpp` - JNI entry point for parameter setting
- `CubismBreath.cpp` - Uses AddParameterValue (adds offset, doesn't replace)
