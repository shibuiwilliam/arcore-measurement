package com.shibuiwilliam.arcoremeasurement

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
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
import com.google.ar.sceneform.rendering.Color as arColor
import java.util.Objects
import kotlin.math.pow
import kotlin.math.sqrt


class Measurement : AppCompatActivity(), Scene.OnUpdateListener {
    private val MIN_OPENGL_VERSION = 3.0
    private val TAG: String = Measurement::class.java.getSimpleName()

    private var arFragment: ArFragment? = null
    private var distanceModeTextView: TextView? = null

    private lateinit var distanceCardViewRenderable: ViewRenderable
    private lateinit var pointTextView: TextView
    private lateinit var cubeRenderable: ModelRenderable

    private lateinit var distanceModeSpinner: Spinner
    private val distanceModeArrayList = ArrayList<String>()
    private var distanceMode: String = ""


    private val placedAnchors = ArrayList<Anchor>()
    private val placedAnchorNodes = ArrayList<AnchorNode>()
    private val midAnchors = ArrayList<Anchor>()
    private val midAnchorNodes = ArrayList<AnchorNode>()

    private lateinit var clearButton: Button


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
        clearButton()

        arFragment!!.setOnTapArPlaneListener { hitResult: HitResult, plane: Plane?, motionEvent: MotionEvent? ->
            if (cubeRenderable == null) return@setOnTapArPlaneListener
            // Creating Anchor.
            when (distanceMode){
                distanceModeArray[0] -> {
                    clearAllAnchors()
                    placeAnchor(hitResult, plane, motionEvent, distanceCardViewRenderable)
                }
                distanceModeArray[1] -> {
                    tapDistanceOf2Points(hitResult, plane, motionEvent)
                }
                distanceModeArray[2] -> {
                    tapDistanceOfMultiplePoints(hitResult, plane, motionEvent)
                }
                else -> {
                    clearAllAnchors()
                    placeAnchor(hitResult, plane, motionEvent, distanceCardViewRenderable)
                }
            }
        }
    }

    private fun initRenderable() {
        MaterialFactory.makeTransparentWithColor(
            this,
            arColor(Color.RED))
            .thenAccept { material: Material? ->
                cubeRenderable = ShapeFactory.makeSphere(
                    0.01f,
                    Vector3.zero(),
                    material)
                cubeRenderable.setShadowCaster(false)
                cubeRenderable.setShadowReceiver(false)
            }
            .exceptionally {
                val builder = AlertDialog.Builder(this)
                builder.setMessage(it.message).setTitle("Error")
                val dialog = builder.create()
                dialog.show()
                return@exceptionally null
            }

        ViewRenderable
            .builder()
            .setView(this, R.layout.distance_text_layout)
            .build()
            .thenAccept{
                distanceCardViewRenderable = it
                distanceCardViewRenderable.isShadowCaster = false
                distanceCardViewRenderable.isShadowReceiver = false
            }
            .exceptionally {
                val builder = AlertDialog.Builder(this)
                builder.setMessage(it.message).setTitle("Error")
                val dialog = builder.create()
                dialog.show()
                return@exceptionally null
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
                clearAllAnchors()
                setMode()
                toastMode()
                Log.i(TAG, "Selected arcore focus on ${distanceMode}")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                clearAllAnchors()
                setMode()
                toastMode()
            }
        }
    }

    private fun setMode(){
        distanceModeTextView!!.text = distanceMode
    }

    private fun clearButton(){
        clearButton = findViewById(R.id.clearButton)
        clearButton.setOnClickListener(object: View.OnClickListener {
            override fun onClick(v: View?) {
                clearAllAnchors()
            }
        })

    }

    private fun clearAllAnchors(){
        placedAnchors.clear()
        midAnchors.clear()
        for (i in 0 until placedAnchorNodes.size){
            arFragment!!.arSceneView.scene.removeChild(placedAnchorNodes[i])
            placedAnchorNodes[i].anchor!!.detach()
            placedAnchorNodes[i].setParent(null)
        }
        placedAnchorNodes.clear()
        for (i in 0 until midAnchorNodes.size){
            arFragment!!.arSceneView.scene.removeChild(midAnchorNodes[i])
            midAnchorNodes[i].anchor!!.detach()
            midAnchorNodes[i].setParent(null)
        }
        midAnchorNodes.clear()
    }

    private fun placeAnchor(hitResult: HitResult,
                            plane: Plane?,
                            motionEvent: MotionEvent?){
        val anchor = hitResult.createAnchor()
        val anchorNode = AnchorNode(anchor).apply {
            isSmoothed = true
            setParent(arFragment!!.arSceneView.scene)
        }
        placedAnchors.add(anchor)
        placedAnchorNodes.add(anchorNode)

        val node = TransformableNode(arFragment!!.transformationSystem).apply{
            rotationController.isEnabled = false
            scaleController.isEnabled = false
            translationController.isEnabled = true
            renderable = when(distanceMode){
                distanceModeArrayList[0] -> distanceCardViewRenderable
                distanceModeArrayList[1] -> cubeRenderable
                else -> distanceCardViewRenderable
            }
            setParent(anchorNode)
        }

        arFragment!!.arSceneView.scene.addOnUpdateListener(this)
        arFragment!!.arSceneView.scene.addChild(anchorNode)
        node.select()
    }

    private fun placeAnchor(hitResult: HitResult,
                            plane: Plane?,
                            motionEvent: MotionEvent?,
                            renderable: Renderable){
        val anchor = hitResult.createAnchor()
        placedAnchors.add(anchor)

        val anchorNode = AnchorNode(anchor).apply {
            isSmoothed = true
            setParent(arFragment!!.arSceneView.scene)
        }
        placedAnchorNodes.add(anchorNode)

        val node = TransformableNode(arFragment!!.transformationSystem)
            .apply{
                this.rotationController.isEnabled = false
                this.scaleController.isEnabled = false
                this.translationController.isEnabled = if (distanceMode == distanceModeArrayList[2]) true else false
                this.renderable = renderable
                setParent(anchorNode)
            }

        arFragment!!.arSceneView.scene.addOnUpdateListener(this)
        arFragment!!.arSceneView.scene.addChild(anchorNode)
        node.select()
    }


    private fun tapDistanceOf2Points(hitResult: HitResult,
                                     plane: Plane?,
                                     motionEvent: MotionEvent?){
        if (placedAnchorNodes.size == 0){
            placeAnchor(hitResult, plane, motionEvent, cubeRenderable)
        }
        else if (placedAnchorNodes.size == 1){
            placeAnchor(hitResult, plane, motionEvent, cubeRenderable)

            val midPosition = floatArrayOf(
                (placedAnchorNodes[0].worldPosition.x + placedAnchorNodes[1].worldPosition.x) / 2,
                (placedAnchorNodes[0].worldPosition.y + placedAnchorNodes[1].worldPosition.y) / 2,
                (placedAnchorNodes[0].worldPosition.z + placedAnchorNodes[1].worldPosition.z) / 2)
            val quaternion = floatArrayOf(0.0f,0.0f,0.0f,0.0f)
            val pose = Pose(midPosition, quaternion)

            placeMidAnchor(pose, plane, motionEvent, distanceCardViewRenderable)
        }
        else {
            clearAllAnchors()
            placeAnchor(hitResult, plane, motionEvent, cubeRenderable)
        }
    }

    private fun placeMidAnchor(pose: Pose,
                               plane: Plane?,
                               motionEvent: MotionEvent?,
                               renderable: Renderable){
        val anchor = arFragment!!.arSceneView.session!!.createAnchor(pose)
        midAnchors.add(anchor)

        val anchorNode = AnchorNode(anchor).apply {
            isSmoothed = true
            setParent(arFragment!!.arSceneView.scene)
        }
        midAnchorNodes.add(anchorNode)

        val node = TransformableNode(arFragment!!.transformationSystem).apply{
            this.rotationController.isEnabled = false
            this.scaleController.isEnabled = false
            this.translationController.isEnabled = if (distanceMode == distanceModeArrayList[2]) true else false
            this.renderable = renderable
            setParent(anchorNode)
        }
        arFragment!!.arSceneView.scene.addOnUpdateListener(this)
        arFragment!!.arSceneView.scene.addChild(anchorNode)
    }

    private fun tapDistanceOfMultiplePoints(hitResult: HitResult,
                                            plane: Plane?,
                                            motionEvent: MotionEvent?){
        ViewRenderable
            .builder()
            .setView(this, R.layout.point_text_layout)
            .build()
            .thenAccept{
                it.isShadowReceiver = false
                it.isShadowCaster = false
                pointTextView = it.getView() as TextView
                pointTextView.setText(placedAnchors.size.toString())
                placeAnchor(hitResult, plane, motionEvent, it)
            }
            .exceptionally {
                val builder = AlertDialog.Builder(this)
                builder.setMessage(it.message).setTitle("Error")
                val dialog = builder.create()
                dialog.show()
                return@exceptionally null
            }
        Log.i(TAG, "${placedAnchorNodes.size}")
        if (placedAnchorNodes.size > 0){
            for (i in 0 until placedAnchorNodes.size){
                for (j in i+1 until placedAnchorNodes.size){
                    val midPosition = floatArrayOf(
                        (placedAnchorNodes[i].worldPosition.x + placedAnchorNodes[j].worldPosition.x) / 2,
                        (placedAnchorNodes[i].worldPosition.y + placedAnchorNodes[j].worldPosition.y) / 2,
                        (placedAnchorNodes[i].worldPosition.z + placedAnchorNodes[j].worldPosition.z) / 2)
                    val quaternion = floatArrayOf(0.0f,0.0f,0.0f,0.0f)
                    val pose = Pose(midPosition, quaternion)

                    val distanceMeter = calculateDistance(
                        placedAnchorNodes[i].worldPosition,
                        placedAnchorNodes[j].worldPosition)
                    val distanceCM = changeUnit(distanceMeter, "cm")
                    val distanceCMFloor = "${i} - ${j}: %.2f".format(distanceCM)

                    ViewRenderable
                        .builder()
                        .setView(this, R.layout.point_text_layout)
                        .build()
                        .thenAccept{
                            it.isShadowReceiver = false
                            it.isShadowCaster = false
                            pointTextView = it.getView() as TextView
                            pointTextView.setText(distanceCMFloor)
                            placeMidAnchor(pose, plane, motionEvent, it)
                        }
                        .exceptionally {
                            val builder = AlertDialog.Builder(this)
                            builder.setMessage(it.message).setTitle("Error")
                            val dialog = builder.create()
                            dialog.show()
                            return@exceptionally null
                        }
                }
            }
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
            distanceModeArrayList[2] -> {

            }
            else -> {
                measureDistanceFromCamera()
            }
        }
    }

    private fun measureDistanceFromCamera(){
        val frame = arFragment!!.arSceneView.arFrame
        if (placedAnchorNodes.size >= 1) {
            val distanceMeter = calculateDistance(
                placedAnchorNodes[0].worldPosition,
                frame!!.camera.pose)
            measureDistanceOf2Points(distanceMeter)
        }
    }

    private fun measureDistanceOf2Points(){
        if (placedAnchorNodes.size == 2) {
            val distanceMeter = calculateDistance(
                placedAnchorNodes[0].worldPosition,
                placedAnchorNodes[1].worldPosition)
            measureDistanceOf2Points(distanceMeter)
        }
    }

    private fun measureDistanceOf2Points(distanceMeter: Float){
        val distanceCM = changeUnit(distanceMeter, "cm")
        val distanceCMFloor = "%.2f".format(distanceCM)
        val textView = (distanceCardViewRenderable.view as LinearLayout)
            .findViewById<TextView>(R.id.distanceCard)
        textView.text = "${distanceCMFloor} cm"
        Log.d(TAG, "distance: ${distanceCMFloor} cm")
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

    private fun toastMode(){
        Toast.makeText(this@Measurement,
            when(distanceMode){
                distanceModeArrayList[0] -> "Find plane and tap somewhere"
                distanceModeArrayList[1] -> "Find plane and tap 2 points"
                distanceModeArrayList[2] -> "Find plane and tap multiple points"
                else -> "???"
            },
            Toast.LENGTH_LONG)
            .show()
    }


    private fun checkIsSupportedDeviceOrFinish(activity: Activity): Boolean {
        val openGlVersionString =
            (Objects.requireNonNull(activity
                .getSystemService(Context.ACTIVITY_SERVICE)) as ActivityManager)
                .deviceConfigurationInfo
                .glEsVersion
        if (openGlVersionString.toDouble() < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES ${MIN_OPENGL_VERSION} later")
            Toast.makeText(activity,
                "Sceneform requires OpenGL ES ${MIN_OPENGL_VERSION} or later",
                Toast.LENGTH_LONG)
                .show()
            activity.finish()
            return false
        }
        return true
    }
}

fun makeCombinationOf2(arrayList: ArrayList<Int>): ArrayList<Array<Int>>{
    val combArrayList = ArrayList<Array<Int>>()
    for (i in 0 until arrayList.size - 1){
        for (j in i+1 until arrayList.size){
            combArrayList.add(arrayOf(arrayList[i], arrayList[j]))
        }
    }
    return combArrayList
}