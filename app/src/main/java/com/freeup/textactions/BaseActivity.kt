package com.freeup.textactions

import androidx.appcompat.app.AppCompatActivity

/**
 * Base activity — locale is applied globally via AppCompatDelegate.setApplicationLocales()
 * in TextActionsApplication, so no manual context wrapping or recreation is needed here.
 */
abstract class BaseActivity : AppCompatActivity()
