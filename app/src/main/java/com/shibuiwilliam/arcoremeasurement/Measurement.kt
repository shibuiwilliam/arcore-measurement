package com.shibuiwilliam.arcoremeasurement

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.*
import com.google.ar.core.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import java.util.Objects
import kotlin.math.pow
import kotlin.math.sqrt


class Measurement : AppCompatActivity(), Scene.OnUpdateListener {
    private val MIN_OPENGL_VERSION = 3.0
    private val TAG: String = Measurement::class.java.getSimpleName()

    private var arFragment: ArFragment? = null
    private var titleTextView: TextView? = null
    private var distanceModeTextView: TextView? = null
    private var distanceTextView: TextView? = null

    private lateinit var distanceModeSpinner: Spinner
    private val distanceModeArrayList = ArrayList<String>()
    private var distanceMode: String = ""

    private var cubeRenderable: ModelRenderable? = null

    private var placedAnchors = Array<Anchor?>(2){null}
    private var placedAnchorNodes = Array<AnchorNode?>(2){null}


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!checkIsSupportedDeviceOrFinish(this)) {
            Toast.makeText(applicationContext, "Device not supported", Toast.LENGTH_LONG)
                .show()
        }

        setContentView(R.layout.activity_measurement)
        val distanceModeArray = resources.getStringArray(R.array.distance_mode)
        distanceModeArray.map{it->
            distanceModeArrayList.add(it)
        }
        arFragment = supportFragmentManager.findFragmentById(R.id.sceneform_fragment) as ArFragment?
        distanceModeTextView = findViewById(R.id.distance_view)
        distanceTextView = findViewById(R.id.distance)
        titleTextView = findViewById(R.id.title)

        configureSpinner()

        initRenderable()


        arFragment!!.setOnTapArPlaneListener { hitResult: HitResult, plane: Plane?, motionEvent: MotionEvent? ->
            if (cubeRenderable == null) return@setOnTapArPlaneListener
            // Creating Anchor.
            when (distanceMode){
                distanceModeArray[0] -> {
                    clearAllAnchor()
                    placeAnchor(hitResult, plane, motionEvent, 0)
                }
                distanceModeArray[1] -> {
                    tapDistanceOf2Points(hitResult, plane, motionEvent)
                }
                else -> {
                    clearAllAnchor()
                    placeAnchor(hitResult, plane, motionEvent, 0)
                }
            }
        }
    }

    private fun placeAnchor(hitResult: HitResult,
                            plane: Plane?,
                            motionEvent: MotionEvent?,
                            anchorIndex: Int){
        val anchor = hitResult.createAnchor()
        val anchorNode =
            AnchorNode(anchor)
        anchorNode.setParent(arFragment!!.arSceneView.scene)
        placedAnchors[anchorIndex] = anchor
        placedAnchorNodes[anchorIndex] = anchorNode
        val node = TransformableNode(arFragment!!.transformationSystem)
        node.renderable = cubeRenderable
        node.setParent(anchorNode)
        arFragment!!.arSceneView.scene.addOnUpdateListener(this)
        arFragment!!.arSceneView.scene.addChild(anchorNode)
        node.select()
    }

    private fun tapDistanceOf2Points(hitResult: HitResult,
                                     plane: Plane?,
                                     motionEvent: MotionEvent?){
        if (placedAnchorNodes[0] == null){
            clearAllAnchor()
            placeAnchor(hitResult, plane, motionEvent, 0)
        }
        else if (placedAnchorNodes[0] != null && placedAnchorNodes[1] == null){
            placeAnchor(hitResult, plane, motionEvent, 1)
        }
        else {
            clearAllAnchor()
            placeAnchor(hitResult, plane, motionEvent, 0)
        }
    }



    private fun initRenderable() {
        MaterialFactory.makeTransparentWithColor(
            this,
            com.google.ar.sceneform.rendering.Color(Color.RED)
        )
            .thenAccept { material: Material? ->
                val vector3 = Vector3(0.05f, 0.01f, 0.01f)
                cubeRenderable = ShapeFactory.makeCube(vector3, Vector3.zero(), material)
                cubeRenderable!!.setShadowCaster(false)
                cubeRenderable!!.setShadowReceiver(false)
            }
    }

    private fun configureSpinner(){
        distanceMode = distanceModeArrayList[0]
        distanceModeSpinner = findViewById(R.id.distance_mode_spinner)
        val distanceModeAdapter = ArrayAdapter(
            applicationContext,
            android.R.layout.simple_spinner_item,
            distanceModeArrayList
        )
        distanceModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        distanceModeSpinner.adapter = distanceModeAdapter
        distanceModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long) {
                val spinnerParent = parent as Spinner
                distanceMode = spinnerParent.selectedItem as String
                when(distanceMode){
                    distanceModeArrayList[0] ->{
                        setMode()
                    }
                    distanceModeArrayList[1] -> {
                        setMode()
                    }
                    else -> {
                        setMode()
                    }
                }
                Log.i(TAG, "Selected arcore focus on ${distanceMode}")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                setMode()
            }
        }
    }

    private fun setMode(){
        titleTextView!!.text = distanceMode
        distanceModeTextView!!.text = distanceMode
    }

    private fun clearAllAnchor(){
        for (i in 0 until placedAnchors.size){
            clearAnchor(i)
        }
    }

    private fun clearAnchor(anchorIndex: Int) {
        placedAnchors[anchorIndex] = null
        if (placedAnchorNodes[anchorIndex] != null) {
            arFragment!!.arSceneView.scene.removeChild(placedAnchorNodes[anchorIndex])
            placedAnchorNodes[anchorIndex]!!.anchor!!.detach()
            placedAnchorNodes[anchorIndex]!!.setParent(null)
            placedAnchorNodes[anchorIndex] = null
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onUpdate(frameTime: FrameTime) {
        when(distanceMode) {
            distanceModeArrayList[0] -> {
                measureDistanceFromCamera()
            }
            distanceModeArrayList[1] -> {
                measureDistanceOf2Points()
            }
            else -> {
                measureDistanceFromCamera()
            }
        }
    }

    private fun measureDistanceFromCamera(){
        val frame = arFragment!!.arSceneView.arFrame
        if (placedAnchorNodes[0] != null) {
            val objectPose = placedAnchors[0]!!.pose
            val cameraPose = frame!!.camera.pose
            val distanceMeter = calculateDistance(objectPose, cameraPose)
            val distanceCM = changeUnit(distanceMeter, "cm")
            val distanceCMFloor = "%.2f".format(distanceCM)
            distanceTextView!!.text = "${distanceCMFloor} cm"
            Log.d(TAG, "distance: ${distanceCMFloor} cm")
        }
        else {
            distanceTextView!!.text = "Tap somewhere"
        }
    }

    private fun measureDistanceOf2Points(){
        if (placedAnchorNodes[0] != null && placedAnchorNodes[1] != null) {
            val objectPose0 = placedAnchors[0]!!.pose
            val objectPose1 = placedAnchors[1]!!.pose
            val distanceMeter = calculateDistance(objectPose0, objectPose1)
            val distanceCM = changeUnit(distanceMeter, "cm")
            val distanceCMFloor = "%.2f".format(distanceCM)
            distanceTextView!!.text = "${distanceCMFloor} cm"
            Log.d(TAG, "distance: ${distanceCMFloor} cm")
        }
        else {
            distanceTextView!!.text = "Tap 2 points"
        }
    }

    private fun calculateDistance(objectPose0: Pose, objectPose1: Pose): Float{
        val dx = objectPose0.tx() - objectPose1.tx()
        val dy = objectPose0.ty() - objectPose1.ty()
        val dz = objectPose0.tz() - objectPose1.tz()
        val distanceMeter =
            sqrt(dx.pow(2) + dy.pow(2) + dz.pow(2))
        return distanceMeter
    }

    private fun changeUnit(distanceMeter: Float, unit: String): Float{
        return when(unit){
            "cm" -> distanceMeter * 100
            "mm" -> distanceMeter * 1000
            else -> distanceMeter
        }
    }

    private fun checkIsSupportedDeviceOrFinish(activity: Activity): Boolean {
        val openGlVersionString =
            (Objects.requireNonNull(activity
                .getSystemService(Context.ACTIVITY_SERVICE)) as ActivityManager)
                .deviceConfigurationInfo
                .glEsVersion
        if (openGlVersionString.toDouble() < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later")
            Toast.makeText(activity,
                "Sceneform requires OpenGL ES 3.0 or later",
                Toast.LENGTH_LONG)
                .show()
            activity.finish()
            return false
        }
        return true
    }
}
