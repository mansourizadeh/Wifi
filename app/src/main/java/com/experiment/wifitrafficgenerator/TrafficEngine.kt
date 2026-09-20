package com.experiment.wifitrafficgenerator

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

data class Stats(val bytes:Long,val currentMbps:Double,val averageMbps:Double)

abstract class TrafficEngine(protected val finished:(String)->Unit) {
    val running=AtomicBoolean(false)
    @Volatile var bytes=0L
    protected var started=0L
    private var lastBytes=0L
    private var lastTime=0L
    abstract fun start()
    abstract fun stop()
    fun stats():Stats {
        val now=System.currentTimeMillis(); val elapsed=(now-started).coerceAtLeast(1)/1000.0
        val dt=(now-lastTime).coerceAtLeast(1)/1000.0
        val delta=bytes-lastBytes; lastBytes=bytes; lastTime=now
        return Stats(bytes,delta*8.0/(dt*1e6),bytes*8.0/(elapsed*1e6))
    }
    protected fun init(){ running.set(true); bytes=0; started=System.currentTimeMillis(); lastBytes=0; lastTime=started }
}
class SenderEngine(private val host:String,private val port:Int,private val seconds:Int,finished:(String)->Unit):TrafficEngine(finished){
    private val pool=Executors.newSingleThreadExecutor(); private var socket:Socket?=null
    override fun start(){ init(); pool.execute {
        try {
            socket=Socket(); socket!!.connect(InetSocketAddress(host,port),5000)
            val out:OutputStream=socket!!.getOutputStream(); val data=ByteArray(64*1024); Random.nextBytes(data)
            while(running.get() && (seconds<=0 || System.currentTimeMillis()-started < seconds*1000L)){ out.write(data); bytes+=data.size }
        } catch(e:Exception) { if(running.get()) finished(e.localizedMessage ?: "Connection failed") }
        finally { running.set(false); try{socket?.close()}catch(_:Exception){}; finished("Completed") }
    }}
    override fun stop(){ running.set(false); try{socket?.close()}catch(_:Exception){} }
}
class ReceiverEngine(private val port:Int,finished:(String)->Unit):TrafficEngine(finished){
    private val pool=Executors.newSingleThreadExecutor(); private var server:ServerSocket?=null; private var socket:Socket?=null
    override fun start(){ init(); pool.execute {
        try {
            server=ServerSocket(port); socket=server!!.accept(); val input:InputStream=socket!!.getInputStream(); val data=ByteArray(64*1024)
            while(running.get()){ val n=input.read(data); if(n<0) break; bytes+=n }
        } catch(e:Exception) { if(running.get()) finished(e.localizedMessage ?: "Receiver error") }
        finally { running.set(false); try{socket?.close();server?.close()}catch(_:Exception){}; finished("Sender disconnected") }
    }}
    override fun stop(){ running.set(false); try{socket?.close();server?.close()}catch(_:Exception){} }
}
