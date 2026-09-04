package com.openapplocktest.applock

import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext

class BackArrowViewManager : SimpleViewManager<BackArrowView>() {

    override fun getName(): String {
        return "OpenAppLockBackArrow"
    }

    override fun createViewInstance(
        reactContext: ThemedReactContext
    ): BackArrowView {
        return BackArrowView(reactContext)
    }
}
