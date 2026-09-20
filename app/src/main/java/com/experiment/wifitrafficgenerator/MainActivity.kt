package com.experiment.wifitrafficgenerator

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var ip: EditText
    private lateinit var port: EditText
    private lateinit var duration: EditText
    private lateinit var start: Button
    private lateinit var status: TextView
    private lateinit var stats: TextView
    private lateinit var localIp: TextView
    private var senderMode=true
    private var engine: TrafficEngine?=null
    private val handler=Handler(Looper.getMainLooper())
    private val ticker=object: Runnable {
        override fun run() {
            engine?.let {
                val s=it.stats()
                stats.text=String.format(Locale.US,"Transferred: %.1f MB\nCurrent: %.1f Mbps\nAverage: %.1f Mbps",
                    s.bytes/1048576.0,s.currentMbps,s.averageMbps)
                if(it.running.get()) handler.postDelayed(this,500)
            }
        }
    }
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); setContentView(R.layout.activity_main)
        ip=findViewById(R.id.ip); port=findViewById(R.id.port); duration=findViewById(R.id.duration)
        start=findViewById(R.id.start); status=findViewById(R.id.status); stats=findViewById(R.id.stats); localIp=findViewById(R.id.localIp)
        localIp.text="Local IP: "+getLocalIp()
        findViewById<Button>(R.id.sender).setOnClickListener { senderMode=true; ip.isEnabled=true; duration.isEnabled=true; status.text="SENDER MODE" }
        findViewById<Button>(R.id.receiver).setOnClickListener { senderMode=false; ip.isEnabled=false; duration.isEnabled=false; status.text="RECEIVER MODE — waiting" }
        start.setOnClickListener { if(engine?.running?.get()==true) stop("Stopped") else startTraffic() }
    }
    private fun startTraffic() {
        val p=port.text.toString().toIntOrNull()?.coerceIn(1,65535) ?: 5001
        val seconds=duration.text.toString().toIntOrNull() ?: 30
        engine=if(senderMode) SenderEngine(ip.text.toString().trim(),p,seconds) { done -> runOnUiThread { stop(done) } }
               else ReceiverEngine(p) { done -> runOnUiThread { stop(done) } }
        engine!!.start(); start.text="STOP TRAFFIC"; status.text=if(senderMode)"SENDING TCP DATA" else "LISTENING ON PORT $p"; handler.post(ticker)
    }
    private fun stop(reason:String) { engine?.stop(); handler.removeCallbacks(ticker); start.text="START TRAFFIC"; status.text=reason }
    override fun onDestroy(){ engine?.stop(); handler.removeCallbacks(ticker); super.onDestroy() }
    private fun getLocalIp():String {
        return try {
            Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap { Collections.list(it.inetAddresses) }
                .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(":") == false }?.hostAddress ?: "127.0.0.1"
        } catch(e:Exception) { "127.0.0.1" }
    }
}
