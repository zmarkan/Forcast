package com.enkhee.forecastmvvm.data.repository

import androidx.lifecycle.LiveData
import com.enkhee.forecastmvvm.data.db.CurrentWeatherDao
import com.enkhee.forecastmvvm.data.db.WeatherLocationDao
import com.enkhee.forecastmvvm.data.db.entity.WeatherLocation
import com.enkhee.forecastmvvm.data.db.unitlocalized.UnitSpecificCurrentWeatherEntry
import com.enkhee.forecastmvvm.data.network.WeatherNetworkDataSource
import com.enkhee.forecastmvvm.data.network.response.CurrentWeatherResponse
import com.enkhee.forecastmvvm.data.provider.LocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.ZonedDateTime
import java.util.*

class ForecastRepositoryImpl(
    private val currentWeatherDao : CurrentWeatherDao,
    private val weatherLocationDao : WeatherLocationDao,
    private val weatherNetworkDataSource : WeatherNetworkDataSource,
    private val locationProvider : LocationProvider
) : ForecastRepository {

    override suspend fun getWeatherLocation() : LiveData<out WeatherLocation> {
        return withContext(Dispatchers.IO) {
            return@withContext weatherLocationDao.getLocation()
        }
    }

    init {
        weatherNetworkDataSource.downloadedCurrentWeather.observeForever { newCurrentWeather ->
            persistFetchedCurrentWeather(newCurrentWeather)
        }
    }

    override suspend fun getCurrentWeather(metric : Boolean) : LiveData<out UnitSpecificCurrentWeatherEntry> {
        return withContext(Dispatchers.IO) {
            initWeatherData()
            return@withContext if (metric) currentWeatherDao.getWeatherMetric()
            else currentWeatherDao.getWeatherImperial()
        }
    }

    private fun persistFetchedCurrentWeather(fetchedWeather : CurrentWeatherResponse) {
        GlobalScope.launch(Dispatchers.IO) {
            currentWeatherDao.upsert(fetchedWeather.currentWeatherEntry)
            weatherLocationDao.upsert(fetchedWeather.location)
        }
    }

    private suspend fun fetchCurrentWeather() {
        weatherNetworkDataSource.fetchCurrentWeather(
            locationProvider.getPreferredLocationString(),
            Locale.getDefault().language
        )
    }

    private suspend fun initWeatherData() {
        val lastWeatherLocation = weatherLocationDao.getLocation().value
        if (lastWeatherLocation == null
            || locationProvider.hasLocationChanged(lastWeatherLocation)) {
            fetchCurrentWeather()
            return
        }
        if (isFetchCurrentNeeded(lastWeatherLocation.zonedDateTime))
            fetchCurrentWeather()
    }

    private fun isFetchCurrentNeeded(lastFetchTime : ZonedDateTime) : Boolean {
        val thirtyMinutesAgo = ZonedDateTime.now().minusMinutes(30)
        return lastFetchTime.isBefore(thirtyMinutesAgo)
    }
}