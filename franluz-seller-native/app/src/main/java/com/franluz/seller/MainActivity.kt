package com.franluz.seller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.franluz.seller.ui.FranLuzSellerTheme
import com.franluz.seller.ui.SellerApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FranLuzSellerTheme {
                SellerApp()
            }
        }
    }
}
