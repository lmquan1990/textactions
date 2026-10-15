package com.freeup.textactions

import android.os.Bundle
import android.content.Intent
import com.freeup.textactions.databinding.ActivityActionListBinding

class ActionListActivity : BaseActivity() {

    private lateinit var binding: ActivityActionListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActionListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnMenu.setOnClickListener {
            startActivity(Intent(this, UserGuideActivity::class.java))
        }
    }
}
