package com.shibuiwilliam.arcoremeasurement

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


    private var currentAnchor: Anchor? = null
    private var currentAnchorNode: AnchorNode? = null

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

        initModel()

        


        arFragment!!.setOnTapArPlaneListener { hitResult: HitResult, plane: Plane?, motionEvent: MotionEvent? ->
            if (cubeRenderable == null) return@setOnTapArPlaneListener
            // Creating Anchor.
            val anchor = hitResult.createAnchor()
            val anchorNode =
                AnchorNode(anchor)
            anchorNode.setParent(arFragment!!.arSceneView.scene)
            clearAnchor()
            currentAnchor = anchor
            currentAnchorNode = anchorNode
            val node = TransformableNode(arFragment!!.transformationSystem)
            node.renderable = cubeRenderable
            node.setParent(anchorNode)
            arFragment!!.arSceneView.scene.addOnUpdateListener(this)
            arFragment!!.arSceneView.scene.addChild(anchorNode)
            node.select()
        }
    }

    fun checkIsSupportedDeviceOrFinish(activity: Activity): Boolean {
        val openGlVersionString =
            (Objects.requireNonNull(activity.getSystemService(Context.ACTIVITY_SERVICE)) as ActivityManager)
                .deviceConfigurationInfo
                .glEsVersion
        if (openGlVersionString.toDouble() < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later")
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                .show()
            activity.finish()
            return false
        }
        return true
    }

    private fun initModel() {
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
                        distanceMode = distanceModeArrayList[0]
                        titleTextView!!.text = distanceModeArrayList[0]
                        distanceModeTextView!!.text = distanceModeArrayList[0]
                    }
                    distanceModeArrayList[1] -> {
                        distanceMode = distanceModeArrayList[1]
                        titleTextView!!.text = distanceModeArrayList[1]
                        distanceModeTextView!!.text = distanceModeArrayList[1]
                    }
                    else -> {
                        distanceMode = distanceModeArrayList[0]
                        titleTextView!!.text = distanceModeArrayList[0]
                        distanceModeTextView!!.text = distanceModeArrayList[0]
                    }
                }
                Log.i(TAG, "Selected arcore focus on ${distanceMode}")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                distanceMode = distanceModeArrayList[0]
                titleTextView!!.text = distanceModeArrayList[0]
                distanceModeTextView!!.text = distanceModeArrayList[0]
            }
        }
    }

    private fun clearAnchor() {
        currentAnchor = null
        if (currentAnchorNode != null) {
            arFragment!!.arSceneView.scene.removeChild(currentAnchorNode)
            currentAnchorNode!!.anchor!!.detach()
            currentAnchorNode!!.setParent(null)
            currentAnchorNode = null
        }
    }

    override fun onUpdate(frameTime: FrameTime) {
        val frame = arFragment!!.arSceneView.arFrame
        Log.d(
            TAG,
            "onUpdateframe... current anchor node " + (currentAnchorNode == null)
        )
        if (currentAnchorNode != null) {
            val objectPose = currentAnchor!!.pose
            val cameraPose = frame!!.camera.pose
            val dx = objectPose.tx() - cameraPose.tx()
            val dy = objectPose.ty() - cameraPose.ty()
            val dz = objectPose.tz() - cameraPose.tz()
            ///Compute the straight-line distance.
            val distanceCM =
                Math.sqrt(dx * dx + dy * dy + (dz * dz).toDouble()).toFloat() * 100
            distanceTextView!!.text = "${"%.2f".format(distanceCM)} cm"
        }
    }
}
