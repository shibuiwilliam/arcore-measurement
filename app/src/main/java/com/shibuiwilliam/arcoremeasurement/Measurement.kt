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
import com.google.ar.sceneform.HitTestResult
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.math.Quaternion
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
    private var distanceModeTextView: TextView? = null

    private lateinit var distanceCardViewRenderable: ViewRenderable

    private lateinit var distanceModeSpinner: Spinner
    private val distanceModeArrayList = ArrayList<String>()
    private var distanceMode: String = ""

    private var cubeRenderable: ModelRenderable? = null

    private var placedAnchors = Array<Anchor?>(2){null}
    private var placedAnchorNodes = Array<AnchorNode?>(2){null}
    private var midAnchors = Array<Anchor?>(2){null}
    private var midAnchorNodes = Array<AnchorNode?>(2){null}


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
        val anchorNode = AnchorNode(anchor).apply {
            isSmoothed = true
            setParent(arFragment!!.arSceneView.scene)
        }
        placedAnchors[anchorIndex] = anchor
        placedAnchorNodes[anchorIndex] = anchorNode
        val node = TransformableNode(arFragment!!.transformationSystem).apply{
            rotationController.isEnabled = false
            scaleController.isEnabled = false
            translationController.isEnabled = true
            renderable = when(distanceMode){
                distanceModeArrayList[0] -> distanceCardViewRenderable
                else -> cubeRenderable
            }
            setParent(anchorNode)
        }
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

            val midPosition = floatArrayOf(
                (placedAnchorNodes[0]!!.worldPosition.x + placedAnchorNodes[1]!!.worldPosition.x) / 2,
                (placedAnchorNodes[0]!!.worldPosition.y + placedAnchorNodes[1]!!.worldPosition.y) / 2,
                (placedAnchorNodes[0]!!.worldPosition.z + placedAnchorNodes[1]!!.worldPosition.z) / 2)
            val quaternion = floatArrayOf(0.0f,0.0f,0.0f,0.0f)

            val pose = Pose(midPosition, quaternion)
            val anchor = arFragment!!.arSceneView.session!!.createAnchor(pose)
            val anchorNode = AnchorNode(anchor).apply {
                isSmoothed = true
                setParent(arFragment!!.arSceneView.scene)
            }

            midAnchors[0] = anchor
            midAnchorNodes[0] = anchorNode
            val node = TransformableNode(arFragment!!.transformationSystem).apply{
                rotationController.isEnabled = false
                scaleController.isEnabled = false
                translationController.isEnabled = true
                renderable = distanceCardViewRenderable
                setParent(anchorNode)
            }
            arFragment!!.arSceneView.scene.addOnUpdateListener(this)
            arFragment!!.arSceneView.scene.addChild(anchorNode)
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
                cubeRenderable = ShapeFactory.makeSphere(0.01f, Vector3.zero(), material)
                cubeRenderable!!.setShadowCaster(false)
                cubeRenderable!!.setShadowReceiver(false)
            }

        ViewRenderable
            .builder()
            .setView(this, R.layout.distance_text_layout)
            .build()
            .thenAccept{
                distanceCardViewRenderable = it
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
                        clearAllAnchor()
                        setMode()
                    }
                    distanceModeArrayList[1] -> {
                        clearAllAnchor()
                        setMode()
                    }
                    else -> {
                        clearAllAnchor()
                        setMode()
                    }
                }
                Log.i(TAG, "Selected arcore focus on ${distanceMode}")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                clearAllAnchor()
                setMode()
            }
        }
    }

    private fun setMode(){
        distanceModeTextView!!.text = distanceMode
    }

    private fun clearAllAnchor(){
        for (i in 0 until placedAnchors.size){
            clearAnchor(i)
        }
    }

    private fun clearAnchor(anchorIndex: Int) {
        placedAnchors[anchorIndex] = null
        midAnchors[anchorIndex] = null
        if (placedAnchorNodes[anchorIndex] != null) {
            arFragment!!.arSceneView.scene.removeChild(placedAnchorNodes[anchorIndex])
            placedAnchorNodes[anchorIndex]!!.anchor!!.detach()
            placedAnchorNodes[anchorIndex]!!.setParent(null)
            placedAnchorNodes[anchorIndex] = null
        }
        if (midAnchorNodes[anchorIndex] != null) {
            arFragment!!.arSceneView.scene.removeChild(midAnchorNodes[anchorIndex])
            midAnchorNodes[anchorIndex]!!.anchor!!.detach()
            midAnchorNodes[anchorIndex]!!.setParent(null)
            midAnchorNodes[anchorIndex] = null
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
            val objectPose = placedAnchorNodes[0]!!.worldPosition
            val cameraPose = frame!!.camera.pose
            val distanceMeter = calculateDistance(objectPose, cameraPose)
            val distanceCM = changeUnit(distanceMeter, "cm")
            val distanceCMFloor = "%.2f".format(distanceCM)
            val textView = (distanceCardViewRenderable.view as LinearLayout)
                .findViewById<TextView>(R.id.distanceCard)
            textView.text = "${distanceCMFloor} cm"
            Log.d(TAG, "distance: ${distanceCMFloor} cm")
        }
        else {
            Toast.makeText(this,
                "Find plane and tap somewhere",
                Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun measureDistanceOf2Points(){
        if (placedAnchorNodes[0] != null && placedAnchorNodes[1] != null) {
            val objectPose0 = placedAnchorNodes[0]!!.worldPosition
            val objectPose1 = placedAnchorNodes[1]!!.worldPosition
            val distanceMeter = calculateDistance(objectPose0, objectPose1)
            val distanceCM = changeUnit(distanceMeter, "cm")
            val distanceCMFloor = "%.2f".format(distanceCM)
            val textView = (distanceCardViewRenderable.view as LinearLayout)
                .findViewById<TextView>(R.id.distanceCard)
            textView.text = "${distanceCMFloor} cm"
            Log.d(TAG, "distance: ${distanceCMFloor} cm")
        }
        else {
            Toast.makeText(this,
                "Find plane and tap 2 points",
                Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun calculateDistance(x: Float, y: Float, z: Float): Float{
        return sqrt(x.pow(2) + y.pow(2) + z.pow(2))
    }

    private fun calculateDistance(objectPose0: Pose, objectPose1: Pose): Float{
        return calculateDistance(
            objectPose0.tx() - objectPose1.tx(),
            objectPose0.ty() - objectPose1.ty(),
            objectPose0.tz() - objectPose1.tz())
    }


    private fun calculateDistance(objectPose0: Vector3, objectPose1: Pose): Float{
        return calculateDistance(
            objectPose0.x - objectPose1.tx(),
            objectPose0.y - objectPose1.ty(),
            objectPose0.z - objectPose1.tz()
        )
    }

    private fun calculateDistance(objectPose0: Vector3, objectPose1: Vector3): Float{
        return calculateDistance(
            objectPose0.x - objectPose1.x,
            objectPose0.y - objectPose1.y,
            objectPose0.z - objectPose1.z
        )
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
