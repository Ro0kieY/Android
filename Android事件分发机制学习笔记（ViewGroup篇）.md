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

                        // 只有两种情况下子View会接收事件的分发
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
                        // 上面方法中又会调用child.dispatchTouchEvent()方法
                        // 如果child为ViewGroup，则继续调用dispatchTouchEvent()方法
                        // 如果child为View，就会调用onTouchEvent()方法
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
                            // 将child赋值给newTouchTarget，同时mFirstTouchTarget也指向child
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

            // 非ACTION_DOWN的事件将会在这里处理
            if (mFirstTouchTarget == null) {
                // 如果FirstTouchTarget为null
                // 调用dispatchTransformedTouchEvent()方法
                // 第三个参数为null，会调用super.dispatchTouchEvent()方法，即交给ViewGroup处理
                handled = dispatchTransformedTouchEvent(ev, canceled, null,
                        TouchTarget.ALL_POINTER_IDS);
            } else {
                // Dispatch to touch targets, excluding the new touch target if we already
                // dispatched to it.  Cancel touch targets if necessary.
                TouchTarget predecessor = null;
                TouchTarget target = mFirstTouchTarget;
                while (target != null) {
                    final TouchTarget next = target.next;
                    // alreadyDispatchedToNewTouchTarget有值，newTouchTarget也有值
                    // 说明子View已经消耗了Down事件，直接设置handled为true
                    if (alreadyDispatchedToNewTouchTarget && target == newTouchTarget) {
                        handled = true;
                    } else {
                        final boolean cancelChild = resetCancelNextUpFlag(target.child)
                                || intercepted;
                        // 否则调用dispatchTransformedTouchEvent()，传递给子View
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