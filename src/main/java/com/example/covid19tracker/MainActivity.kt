package com.example.covid19tracker

import android.nfc.Tag
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.google.gson.GsonBuilder
import com.robinhood.ticker.TickerUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.android.synthetic.main.activity_main.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private const val BASE_URL ="https://api.covidtracking.com/v1/"
private  const val TAG = "MainActivity"
private const val ALL_STATES = "All(Nationwide)"
class MainActivity : AppCompatActivity() {

    private lateinit var currentlyShownData: List<CovidData>
    private lateinit var adapter: covidSparkAdapter

    //  private lateinit var binding: ActivityMainBinding
    private lateinit var perStateDailyData: Map<String, List<CovidData>>
    private lateinit var nationalDailyData: List<CovidData>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //binding = ActivityMainBinding.inflate(layoutInflater)
       // setContentView(binding.root)
       // val metricLabel = findViewById<TextView>(R.id.tvMetricLabel)


        val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create()
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val covidService = retrofit.create(CovidService::class.java)


        // Fetch national data
        covidService.getNationalData().enqueue(object : Callback<List<CovidData>> {
            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure $t")
            }

            override fun onResponse(call: Call<List<CovidData>>, response: Response<List<CovidData>>) {
                Log.i(TAG, "onResponse $response")
                val nationalData = response.body()
                if(nationalData==null){
                    Log.w(TAG,"Did not receive a valid response")
                    return
                }
                setUpEventListener()
                nationalDailyData = nationalData.reversed()
                Log.i(TAG, "Update the graph with national data")
                updateDisplayWithData(nationalDailyData)
                
            }

        })

        // Fetch states data
        covidService.getStatesData().enqueue(object : Callback<List<CovidData>> {
            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure $t")
            }

            override fun onResponse(call: Call<List<CovidData>>, response: Response<List<CovidData>>) {
                Log.i(TAG, "onResponse $response")
                val statesData = response.body()
                if(statesData==null){
                    Log.w(TAG,"Did not receive a valid response")
                    return
                }
                perStateDailyData = statesData.reversed().groupBy { it.state }
                Log.i(TAG, "Update the spinner with state names")
                // Update the spinner with state's data
                updateSpinnerWithStateData(perStateDailyData.keys)

            }
            
        })


    }

    private fun updateSpinnerWithStateData(stateNames: Set<String>) {
        val stateAbbreviationList = stateNames.toMutableList()
        stateAbbreviationList.sort()
        stateAbbreviationList.add(0,ALL_STATES)

        // use the ordered state list as a data source of the spinner
        spinnerSelect.attachDataSource(stateAbbreviationList)
        spinnerSelect.setOnSpinnerItemSelectedListener { parent, _, position, _ ->
            val selectedState = parent.getItemAtPosition(position) as String
            val selectedData = perStateDailyData[selectedState] ?: nationalDailyData
            updateDisplayWithData(selectedData)
        }

    }

    private fun setUpEventListener() {
        tvMetricLabel.setCharacterLists(TickerUtils.provideNumberList())

        // Add listener for user scrubbing over the chart
        sparkView.isScrubEnabled = true
        sparkView.setScrubListener {itemData ->
            if (itemData is CovidData){
                updateInfoForDate(itemData)
            }
        }
        //Respond to radio button selected event
        rbGroup2.setOnCheckedChangeListener{_, checkedId ->
            adapter.daysAgo = when(checkedId){
                R.id.rbWeek -> TimeScale.WEEK
                R.id.rbMonth -> TimeScale.MONTH
                else->TimeScale.MAX
            }
            adapter.notifyDataSetChanged()
        }
        rbGroup.setOnCheckedChangeListener{_, checkedId ->
            when(checkedId){
                R.id.rbPositive -> updateDisplayMetric(Metric.POSITIVE)
                R.id.rbNegative -> updateDisplayMetric(Metric.NEGATIVE)
                R.id.rbDeath -> updateDisplayMetric(Metric.DEATH)
            }
        }
    }

    private fun updateDisplayMetric(metric: Metric) {
        val colorRes = when(metric){
            Metric.DEATH -> R.color.death
            Metric.NEGATIVE-> R.color.negative
            Metric.POSITIVE -> R.color.positive
        }

        // update the color of the chart
        @ColorInt
        val colorInt = ContextCompat.getColor(this, colorRes )
        sparkView.lineColor = colorInt
        tvMetricLabel.setTextColor(colorInt)


        // update the metric on the adapter
        adapter.metric = metric
        adapter.notifyDataSetChanged()

        // reset the date and number shown in the bottom text views
        updateInfoForDate(currentlyShownData.last())
    }

    private fun updateDisplayWithData(dailyData: List<CovidData>) {
        currentlyShownData = dailyData
    // Create a new spark adapter with data
        adapter = covidSparkAdapter(dailyData)
        sparkView.adapter = adapter

    // Update the radio button to display positive and max cases by default
        rbPositive.isChecked = true
        rbMax.isChecked = true

    // Display metric for the most recent date
     updateDisplayMetric(Metric.POSITIVE)
        
    }

    private fun updateInfoForDate(covidData: CovidData) {
        val numCases = when (adapter.metric){
            Metric.NEGATIVE -> covidData.negativeIncrease
            Metric.POSITIVE -> covidData.positiveIncrease
            Metric.DEATH -> covidData.deathIncrease
        }

        tvMetricLabel.text = NumberFormat.getInstance().format(numCases)

        val outputDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
       tvDatelabel.text =  outputDateFormat.format(covidData.dateChecked)
    }
}