package com.freeup.textactions

import android.os.Bundle
import com.freeup.textactions.databinding.ActivityUserGuideBinding

class UserGuideActivity : BaseActivity() {

    private lateinit var binding: ActivityUserGuideBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.btnClose.setOnClickListener {
            finish()
        }
        
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnStartInternal.setOnClickListener {
            finish() // Assuming it just closes the guide for now
        }
    }
}
