## Android事件分发机制学习笔记（ViewGroup篇）

本文是学习Android事件分发机制的学习笔记，一是为了巩固学习成果，加深印象；二是为了方便以后查阅。

****

#### 1. Activity对事件的分发过程

从`Activity#dispatchTouchEvent()`开始看起:

```java
public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            onUserInteraction();
        }
        if (getWindow().superDispatchTouchEvent(ev)) {
            return true;
        }
        return onTouchEvent(ev);
}    
```

首先对`Action_DOWN` 事件进行了特殊判断，调用`onUserInteraction()` ,跟进这个方法，会发现是一个空方法：

```java
public void onUserInteraction() {
}
```

不去管它，接下来Activity会通过`getWindow()` 获得自己所属的`Window`  进行分发，`Window` 是个抽象类，用来控制顶级View的外观和行为策略，它的唯一实现类是`PhoneWindow` 。那么`PhoneWindow` 是如何处理点击事件的，`PhoneWindow#superDispatchTouchEvent()` 如下所示：

```java
@Override
public boolean superDispatchTouchEvent(MotionEvent event) {
    return mDecor.superDispatchTouchEvent(event);
}
```

很简单，直接传递给了`mDecor` ，这个`mDecor` 就是当前窗口最顶层的`DecorView` 。

```java
// This is the top-level view of the window, containing the window decor.
private DecorView mDecor;
```

跟进`DecorView#superDispatchTouchEvent()` :

```java
public boolean superDispatchTouchEvent(MotionEvent event) {
	return super.dispatchTouchEvent(event);
}
```

居然是调用父类的`dispatchTouchEvent()` 方法，`DecorView` 的父类是`FrameLayout` ,继续跟进查看，发现`FrameLayout` 并没有这个方法，那就继续向上追，`FrameLayout` 的父类是`ViewGroup` ，也就是说，触摸事件经过层层传递，最终传递到`ViewGroup#dispatchTouchEvent()` 方法,至此，事件已经传递到视图的顶级View了。

****

#### 2. ViewGroup对事件的分发过程

接下来是重头戏了...上代码`ViewGroup#dispatchTouchEvent()` ...

```java
public boolean dispatchTouchEvent(MotionEvent ev) {
  	// 调试用，不去管它
    if (mInputEventConsistencyVerifier != null) {
        mInputEventConsistencyVerifier.onTouchEvent(ev, 1);
    }

    // 辅助功能，有些用户由于视力上、身体上、年龄上使他们不能接受语音或者视觉信息
    // 不是重点，也不去管它（其实是我不懂...）
    if (ev.isTargetAccessibilityFocus() && isAccessibilityFocusedViewOrHost()) {
        ev.setTargetAccessibilityFocus(false);
    }

    boolean handled = false;
    // onFilterTouchEventForSecurity(ev)，触摸事件安全过滤
    // 具体实现：当窗口被遮挡，返回false，丢弃触摸事件；未被遮挡，返回true    
    if (onFilterTouchEventForSecurity(ev)) {
        // 没有被遮挡
        final int action = ev.getAction();
        final int actionMasked = action & MotionEvent.ACTION_MASK;

        // Handle an initial down.
        if (actionMasked == MotionEvent.ACTION_DOWN) {
            // 如果是Down事件，则重置所有之前保存的状态，因为这是事件序列的开始
            // mFirstTouchTarget会被设为Null
            cancelAndClearTouchTargets(ev);
            // 重置FLAG_DISALLOW_INTERCEPT
            resetTouchState();
        }

        // 检测是否拦截
        final boolean intercepted;
        if (actionMasked == MotionEvent.ACTION_DOWN
                || mFirstTouchTarget != null) {
            // 标记事件不允许被拦截，默认为false
            // 可以由requestDisallowInterceptTouchEvent方法来设置
            // 设置为true，ViewGroup将无法拦截Down以外的点击事件
            final boolean disallowIntercept = (mGroupFlags & FLAG_DISALLOW_INTERCEPT) != 0;
            if (!disallowIntercept) {
                // 调用onInterceptTouchEvent(ev)方法，询问自己是否要拦截事件
                // ViewGroup的onInterceptTouchEvent(ev)方法默认返回false
                intercepted = onInterceptTouchEvent(ev);
                ev.setAction(action); // restore action in case it was changed
            } else {
                intercepted = false;
            }
        } else {
            // There are no touch targets and this action is not an initial down
            // so this view group continues to intercept touches.
            intercepted = true;
        }

            // If intercepted, start normal event dispatch. Also if there is already
            // a view that is handling the gesture, do normal event dispatch.
            if (intercepted || mFirstTouchTarget != null) {
                ev.setTargetAccessibilityFocus(false);
            }

            // 通过标记和Action检查Cancel，将结果赋值给局部变量canceled
            final boolean canceled = resetCancelNextUpFlag(this)
                    || actionMasked == MotionEvent.ACTION_CANCEL;

            // split标记是否需要将事件分发给多个子View，默认为true
            // 可通过setMotionEventSplittingEnabled()方法设置
            final boolean split = (mGroupFlags & FLAG_SPLIT_MOTION_EVENTS) != 0;
            TouchTarget newTouchTarget = null;
            boolean alreadyDispatchedToNewTouchTarget = false;
          
            // 如果没取消也没拦截，进入方法体中
            if (!canceled && !intercepted) {
              
            View childWithAccessibilityFocus = ev.isTargetAccessibilityFocus()
                    ? findChildWithAccessibilityFocus() : null;

            if (actionMasked == MotionEvent.ACTION_DOWN
                    || (split && actionMasked == MotionEvent.ACTION_POINTER_DOWN)
                    || actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
                final int actionIndex = ev.getActionIndex(); // always 0 for down
                final int idBitsToAssign = split ? 1 << ev.getPointerId(actionIndex)
                        : TouchTarget.ALL_POINTER_IDS;

                // Clean up earlier touch targets for this pointer id in case they
                // have become out of sync.
                removePointersFromTouchTargets(idBitsToAssign);
                final int childrenCount = mChildrenCount;
                // 判断newTouchTarget为Null，且ChildrenCount不为0
                if (newTouchTarget == null && childrenCount != 0) {
                    final float x = ev.getX(actionIndex);
                    final float y = ev.getY(actionIndex);
                    // 寻找可以接受触摸事件的子View
                    // 通过buildTouchDispatchChildList()方法构建子View的List集合preorderedList
                    final ArrayList<View> preorderedList = buildTouchDispatchChildList();
                    final boolean customOrder = preorderedList == null
                            && isChildrenDrawingOrderEnabled();
                    final View[] children = mChildren;
                    // 倒序遍历所有的子View
                    for (int i = childrenCount - 1; i >= 0; i--) {
                        final int childIndex = getAndVerifyPreorderedIndex(
                                childrenCount, i, customOrder);
                        final View child = getAndVerifyPreorderedView(
                                preorderedList, children, childIndex);

                        // If there is a view that has accessibility focus we want it
                        // to get the event first and if not handled we will perform a
                        // normal dispatch. We may do a double iteration but this is
                        // safer given the timeframe.
                        if (childWithAccessibilityFocus != null) {
                            if (childWithAccessibilityFocus != child) {
                                continue;
                            }
                            childWithAccessibilityFocus = null;
                            i = childrenCount - 1;
                        }

                        // 同时满足两种情况下子View可以接收事件的分发
                        // canViewReceivePointerEvents()方法会判断子View是否可见和是否在播放动画
                        // isTransformedTouchPointInView()方法会判断触摸事件坐标是否在子View内
                        if (!canViewReceivePointerEvents(child)
                                || !isTransformedTouchPointInView(x, y, child, null)) {
                            ev.setTargetAccessibilityFocus(false);
                            continue;
                        }

                        // 查找当前子View是否在mFirstTouchTarget中存储
                        // 找不到则返回Null
                        newTouchTarget = getTouchTarget(child);
                        if (newTouchTarget != null) {
                            // newTouchTarget不为Nul，说明已经找到接收的View了，break跳出for循环
                            newTouchTarget.pointerIdBits |= idBitsToAssign;
                            break;
                        }
                      
                        resetCancelNextUpFlag(child);
                      
                        // 没有跳出循环，说明我们找到的Child并没有在mFirstTouchTarget中
                        // 调用dispatchTransformedTouchEvent()方法
                        if (dispatchTransformedTouchEvent(ev, false, child, idBitsToAssign)){
                            // Child wants to receive touch within its bounds.
                            mLastTouchDownTime = ev.getDownTime();
                            if (preorderedList != null) {
                                // childIndex points into presorted list, find original index
                                for (int j = 0; j < childrenCount; j++) {
                                    if (children[childIndex] == mChildren[j]) {
                                        mLastTouchDownIndex = j;
                                        break;
                                    }
                                }
                            } else {
                                mLastTouchDownIndex = childIndex;
                            }
                            mLastTouchDownX = ev.getX();
                            mLastTouchDownY = ev.getY();
                            // 将child赋值给newTouchTarget，将child加入mFirstTouchTarget中
                            newTouchTarget = addTouchTarget(child, idBitsToAssign);
                            // alreadyDispatchedToNewTouchTarget赋值为true，跳出循环
                            alreadyDispatchedToNewTouchTarget = true;
                            break;
                        }

                        // The accessibility focus didn't handle the event, so clear
                        // the flag and do a normal dispatch to all children.
                        ev.setTargetAccessibilityFocus(false);
                    }
                    if (preorderedList != null) preorderedList.clear();
                }

                // 没有找到新的可以接收事件的子View，并且之前的mFirstTouchTarget不为空
                // newTouchTarget指向了最初的mFirstTouchTarget
                if (newTouchTarget == null && mFirstTouchTarget != null) {
                    // Did not find a child to receive the event.
                    // Assign the pointer to the least recently added target.
                    newTouchTarget = mFirstTouchTarget;
                    while (newTouchTarget.next != null) {
                        newTouchTarget = newTouchTarget.next;
                    }
                    newTouchTarget.pointerIdBits |= idBitsToAssign;
                }
            }
        }

        if (mFirstTouchTarget == null) {
            // 如果mFirstTouchTarget为null
            // 调用dispatchTransformedTouchEvent()方法
            // 第三个参数为null，会调用super.dispatchTouchEvent()方法
            // 将当前ViewGroup当做普通的View处理
            handled = dispatchTransformedTouchEvent(ev, canceled, null,
                    TouchTarget.ALL_POINTER_IDS);
        } else {
            // Dispatch to touch targets, excluding the new touch target if we already
            // dispatched to it.  Cancel touch targets if necessary.
            TouchTarget predecessor = null;
            TouchTarget target = mFirstTouchTarget;
            while (target != null) {
                final TouchTarget next = target.next;
                if (alreadyDispatchedToNewTouchTarget && target == newTouchTarget) {
                    handled = true;
                } else {
                    final boolean cancelChild = resetCancelNextUpFlag(target.child)
                            || intercepted;
                    if (dispatchTransformedTouchEvent(ev, cancelChild,
                            target.child, target.pointerIdBits)) {
                        handled = true;
                    }
                    if (cancelChild) {
                        if (predecessor == null) {
                            mFirstTouchTarget = next;
                        } else {
                            predecessor.next = next;
                        }
                        target.recycle();
                        target = next;
                        continue;
                    }
                }
                predecessor = target;
                target = next;
            }
        }

        // Update list of touch targets for pointer up or cancel, if needed.
        if (canceled
                || actionMasked == MotionEvent.ACTION_UP
                || actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            resetTouchState();
        } else if (split && actionMasked == MotionEvent.ACTION_POINTER_UP) {
            final int actionIndex = ev.getActionIndex();
            final int idBitsToRemove = 1 << ev.getPointerId(actionIndex);
            // 当某个手指抬起时，清除与它相关的数据
            removePointersFromTouchTargets(idBitsToRemove);
        }
    }

    if (!handled && mInputEventConsistencyVerifier != null) {
        mInputEventConsistencyVerifier.onUnhandledEvent(ev, 1);
    }
    return handled;
}
```
****

#### 3. 事件拦截

`intercepted` 用来标记`ViewGroup` 是否拦截事件，当事件为`MotionEvent.ACTION_DOWN` 或者`mFirstTouchTarget!=null` 时，`if` 判断成立，然后判断`disallowIntercept` 标志位，当`disallowIntercept` 为`false`时，调用`onInterceptTouchEvent()` 方法，并将返回值赋值给`intercepted` ，否则当`disallowIntercept` 为`true`时，则直接将`intercepted` 赋值为`false` 。

`disallowIntercept` 标记位可以通过公共方法 `requestDisallowInterceptTouchEvent()` 设置，通常由子`View`调用，当设置为`True` 后，`ViewGroup` 将无法拦截除`ACTION_DOWN` 之外的点击事件，原因是当事件为`ACTION_DOWN` 时，`ViewGroup` 会重置`disallowIntercept` 标记位，并且将`mFirstTouchTarget` 设置为`null`，因此，当事件为`ACTION_DOWN` 时，`ViewGroup` 总是会调用自己的`onInterceptTouchEvent()`方法。

```java
        if (actionMasked == MotionEvent.ACTION_DOWN) {
            // 如果是Down事件，则重置所有之前保存的状态，因为这是事件序列的开始
            // mFirstTouchTarget会被设为Null
            cancelAndClearTouchTargets(ev);
            // 重置FLAG_DISALLOW_INTERCEPT
            resetTouchState();
        }

        // 检测是否拦截
        final boolean intercepted;
        if (actionMasked == MotionEvent.ACTION_DOWN
                || mFirstTouchTarget != null) {
            // 标记事件不允许被拦截，默认为false
            // 可以由requestDisallowInterceptTouchEvent方法来设置
            // 设置为true，ViewGroup将无法拦截Down以外的点击事件
            final boolean disallowIntercept = (mGroupFlags & FLAG_DISALLOW_INTERCEPT) != 0;
            if (!disallowIntercept) {
                // 调用onInterceptTouchEvent(ev)方法，询问自己是否要拦截事件
                // ViewGroup的onInterceptTouchEvent(ev)方法默认返回false
                intercepted = onInterceptTouchEvent(ev);
                ev.setAction(action); // restore action in case it was changed
            } else {
                intercepted = false;
            }
        } else {
            // There are no touch targets and this action is not an initial down
            // so this view group continues to intercept touches.
            intercepted = true;
        }
```

****

#### 4. 事件分发

中间经过标记和`action`检查`cancel`，将结果赋值给变量`canceled`。`if (!canceled && !intercepted)` 语句表明，事件未被取消且`intercepted`为`false`(未拦截)，则会进入方法体中。

首先判断`childrenCount`不为`0`，然后通过`buildTouchDispatchChildList()`方法拿到子`View`的`List`集合，接着倒序遍历所有子`View`，寻找可以接收点击事件的子`View`，为什么要倒序遍历，是因为`buildTouchDispatchChildList()`内部会调用`buildOrderedChildList()`方法,该方法会将子`View`根据`Z`轴排序，在同一`Z`平面上的子`View`则会根据绘制的先后顺序排序，触摸的时候我们当然会希望浮在最上层的`View`最先响应事件。

对于每一个子`View`来说，需要`canViewReceivePointerEvents()`和`isTransformedTouchPointInView()`均返回`true`，说明子`View`可以接受触摸事件，否则直接`continue`进行下一次循环。`canViewReceivePointerEvents()`通过子`View`是否可见及是否在播放动画来判断子`View`是否可以接收事件，`isTransformedTouchPointInView()`判断触摸事件的坐标点是否在子`View`内，这样就获得了可以接收触摸事件的子`View`。

接下来通过`getTouchTarget(child)`方法判断当前子`View`是否已经在`mFirstTouchTarget`中存储，如果`newTouchTarget`不为null，说明子`View`已经在`mFirstTouchTarget`中，执行`break`跳出循环。

如果`newTouchTarget`为`null`,说明`child`并没有在`mFirstTouchTarget`中保存，此时调用`dispatchTransformedTouchEvent()`方法，该方法十分重要，内部会递归调用`dispatchTouchEvent()`方法，如果子`View`是`ViewGroup`并且事件没有被拦截那么递归调用`dispatchTouchEvent()`;如果子`View`为`View`，那么调用其`onTouchEvent()`。`dispatchTransformedTouchEvent()` 方法是有返回值的，如果返回`true`，说明子View消耗了触摸事件，则在下面的代码中将`child`赋值为`newTouchTarget`，并将`child` 也加入`mFirstTouchEvent` 中，并跳出循环。

```java
            // 如果没取消也没拦截，进入方法体中
            if (!canceled && !intercepted) {
              
            View childWithAccessibilityFocus = ev.isTargetAccessibilityFocus()
                    ? findChildWithAccessibilityFocus() : null;

            if (actionMasked == MotionEvent.ACTION_DOWN
                    || (split && actionMasked == MotionEvent.ACTION_POINTER_DOWN)
                    || actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
                final int actionIndex = ev.getActionIndex(); // always 0 for down
                final int idBitsToAssign = split ? 1 << ev.getPointerId(actionIndex)
                        : TouchTarget.ALL_POINTER_IDS;

                // Clean up earlier touch targets for this pointer id in case they
                // have become out of sync.
                removePointersFromTouchTargets(idBitsToAssign);
                final int childrenCount = mChildrenCount;
                // 判断newTouchTarget为Null，且ChildrenCount不为0
                if (newTouchTarget == null && childrenCount != 0) {
                    final float x = ev.getX(actionIndex);
                    final float y = ev.getY(actionIndex);
                    // 寻找可以接受触摸事件的子View
                    // 通过buildTouchDispatchChildList()方法构建子View的List集合preorderedList
                    final ArrayList<View> preorderedList = buildTouchDispatchChildList();
                    final boolean customOrder = preorderedList == null
                            && isChildrenDrawingOrderEnabled();
                    final View[] children = mChildren;
                    // 倒序遍历所有的子View
                    for (int i = childrenCount - 1; i >= 0; i--) {
                        final int childIndex = getAndVerifyPreorderedIndex(
                                childrenCount, i, customOrder);
                        final View child = getAndVerifyPreorderedView(
                                preorderedList, children, childIndex);

                        // If there is a view that has accessibility focus we want it
                        // to get the event first and if not handled we will perform a
                        // normal dispatch. We may do a double iteration but this is
                        // safer given the timeframe.
                        if (childWithAccessibilityFocus != null) {
                            if (childWithAccessibilityFocus != child) {
                                continue;
                            }
                            childWithAccessibilityFocus = null;
                            i = childrenCount - 1;
                        }

                        // 同时满足两种情况下子View可以接收事件的分发
                        // canViewReceivePointerEvents()方法会判断子View是否可见和是否在播放动画
                        // isTransformedTouchPointInView()方法会判断触摸事件坐标是否在子View内
                        if (!canViewReceivePointerEvents(child)
                                || !isTransformedTouchPointInView(x, y, child, null)) {
                            ev.setTargetAccessibilityFocus(false);
                            continue;
                        }

                        // 查找当前子View是否在mFirstTouchTarget中存储
                        // 找不到则返回Null
                        newTouchTarget = getTouchTarget(child);
                        if (newTouchTarget != null) {
                            // newTouchTarget不为Nul，说明已经找到接收的View了，break跳出for循环
                            newTouchTarget.pointerIdBits |= idBitsToAssign;
                            break;
                        }
                      
                        resetCancelNextUpFlag(child);
                      
                        // 没有跳出循环，说明我们找到的Child并没有在mFirstTouchTarget中
                        // 调用dispatchTransformedTouchEvent()方法
                        if (dispatchTransformedTouchEvent(ev, false, child, idBitsToAssign)){
                            // Child wants to receive touch within its bounds.
                            mLastTouchDownTime = ev.getDownTime();
                            if (preorderedList != null) {
                                // childIndex points into presorted list, find original index
                                for (int j = 0; j < childrenCount; j++) {
                                    if (children[childIndex] == mChildren[j]) {
                                        mLastTouchDownIndex = j;
                                        break;
                                    }
                                }
                            } else {
                                mLastTouchDownIndex = childIndex;
                            }
                            mLastTouchDownX = ev.getX();
                            mLastTouchDownY = ev.getY();
                            // 将child赋值给newTouchTarget，将child加入mFirstTouchTarget中
                            newTouchTarget = addTouchTarget(child, idBitsToAssign);
                            // alreadyDispatchedToNewTouchTarget赋值为true，跳出循环
                            alreadyDispatchedToNewTouchTarget = true;
                            break;
                        }

                        // The accessibility focus didn't handle the event, so clear
                        // the flag and do a normal dispatch to all children.
                        ev.setTargetAccessibilityFocus(false);
                    }
                    if (preorderedList != null) preorderedList.clear();
                }
```
当没有任何子View处理触摸事件时，调用`dispatchTransformedTouchEvent()` 方法，注意此时第三个参数传入null，在方法内部就会调用`super.dispatchTouchEvent()` ,也就是View类的`dispatchTouchEvent()` 。

```java
if (mFirstTouchTarget == null) {
            // 如果mFirstTouchTarget为null
            // 调用dispatchTransformedTouchEvent()方法
            // 第三个参数为null，会调用super.dispatchTouchEvent()方法
            // 将当前ViewGroup当做普通的View处理
            handled = dispatchTransformedTouchEvent(ev, canceled, null,
                    TouchTarget.ALL_POINTER_IDS);
```

****

#### `onInterceptTouchEvent()` 方法

`if`语句判断触摸事件来源是否为鼠标或其他指针式设备，其他情况下默认返回`false` 。

```java
public boolean onInterceptTouchEvent(MotionEvent ev) {
    if (ev.isFromSource(InputDevice.SOURCE_MOUSE)
            && ev.getAction() == MotionEvent.ACTION_DOWN
            && ev.isButtonPressed(MotionEvent.BUTTON_PRIMARY)
            && isOnScrollbarThumb(ev.getX(), ev.getY())) {
        return true;
    }
    return false;
}
```

****

#### `dispatchTransformedTouchEvent()` 方法

`dispatchTransformedTouchEvent()` 源码中发现多次对于child是否为null做判断，并且都做类似的操作：

当`child==null`时，调用`super.dispatchTouchEvent()`，也就是`View`类的`dispatchTouchEvent()`方法，因为`ViewGroup`的父类是`View`；

当`child!=null`时，调用`child.dispatchTouchEvent()`，此时`child`可能是`View`，也可能是`ViewGroup`。

从源码中可以看出`dispatchTransformedTouchEvent()` 方法的返回值，最终取决于`onTouchEvent()`方法，也就是说，`onTouchEvent()`是否被消费了事件，决定了`dispatchTransformedTouchEvent()` 方法的返回值，从而决定`mFirstTouchTarget`是否为`null`，因为如果`dispatchTransformedTouchEvent()` 方法的返回值为`false`，就无法执行`addTouchTarget()`方法，而`mFirstTouchTarget`在`Action_Down`事件到来时被重置为`null`了。

```java
private boolean dispatchTransformedTouchEvent(MotionEvent event, boolean cancel,
            View child, int desiredPointerIdBits) {
        final boolean handled;

        // Canceling motions is a special case.  We don't need to perform any transformations
        // or filtering.  The important part is the action, not the contents.
        final int oldAction = event.getAction();
        if (cancel || oldAction == MotionEvent.ACTION_CANCEL) {
            event.setAction(MotionEvent.ACTION_CANCEL);
            if (child == null) {
                handled = super.dispatchTouchEvent(event);
            } else {
                handled = child.dispatchTouchEvent(event);
            }
            event.setAction(oldAction);
            return handled;
        }

        // Calculate the number of pointers to deliver.
        final int oldPointerIdBits = event.getPointerIdBits();
        final int newPointerIdBits = oldPointerIdBits & desiredPointerIdBits;

        // If for some reason we ended up in an inconsistent state where it looks like we
        // might produce a motion event with no pointers in it, then drop the event.
        if (newPointerIdBits == 0) {
            return false;
        }

        // If the number of pointers is the same and we don't need to perform any fancy
        // irreversible transformations, then we can reuse the motion event for this
        // dispatch as long as we are careful to revert any changes we make.
        // Otherwise we need to make a copy.
        final MotionEvent transformedEvent;
        if (newPointerIdBits == oldPointerIdBits) {
            if (child == null || child.hasIdentityMatrix()) {
                if (child == null) {
                    handled = super.dispatchTouchEvent(event);
                } else {
                    final float offsetX = mScrollX - child.mLeft;
                    final float offsetY = mScrollY - child.mTop;
                    event.offsetLocation(offsetX, offsetY);

                    handled = child.dispatchTouchEvent(event);

                    event.offsetLocation(-offsetX, -offsetY);
                }
                return handled;
            }
            transformedEvent = MotionEvent.obtain(event);
        } else {
            transformedEvent = event.split(newPointerIdBits);
        }

        // Perform any necessary transformations and dispatch.
        if (child == null) {
            handled = super.dispatchTouchEvent(transformedEvent);
        } else {
            final float offsetX = mScrollX - child.mLeft;
            final float offsetY = mScrollY - child.mTop;
            transformedEvent.offsetLocation(offsetX, offsetY);
            if (! child.hasIdentityMatrix()) {
                transformedEvent.transform(child.getInverseMatrix());
            }

            handled = child.dispatchTouchEvent(transformedEvent);
        }

        // Done.
        transformedEvent.recycle();
        return handled;
    }
```