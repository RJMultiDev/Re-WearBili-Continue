package cn.spacexc.wearbili.remake.app.video.info.info.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.spacexc.bilibilisdk.sdk.video.action.VideoAction
import cn.spacexc.bilibilisdk.sdk.video.info.VideoInfo
import cn.spacexc.wearbili.common.domain.log.logd
import cn.spacexc.wearbili.common.domain.network.KtorNetworkUtils
import cn.spacexc.wearbili.remake.app.bangumi.info.ui.BANGUMI_ID_TYPE_EPID
import cn.spacexc.wearbili.remake.app.bangumi.info.ui.BangumiActivity
import cn.spacexc.wearbili.remake.app.bangumi.info.ui.PARAM_BANGUMI_ID
import cn.spacexc.wearbili.remake.app.bangumi.info.ui.PARAM_BANGUMI_ID_TYPE
import cn.spacexc.wearbili.remake.common.ToastUtils
import cn.spacexc.wearbili.remake.common.UIState
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject

/**
 * Created by XC-Qan on 2023/4/12.
 * I'm very cute so please be nice to my code!
 * 给！爷！写！注！释！
 * 给！爷！写！注！释！
 * 给！爷！写！注！释！
 */

@HiltViewModel
class VideoInformationViewModel @Inject constructor(
    private val ktorNetworkUtils: KtorNetworkUtils
) : ViewModel() {
    var state by mutableStateOf(
        VideoInformationScreenState()
    )

    //TODO 把点赞 收藏 投币 三种状态从state里面分离出来
    var isLiked by mutableStateOf(false)
    var isFav by mutableStateOf(false)
    var isCoined by mutableStateOf(false)

    var imageBitmap: Bitmap? by mutableStateOf(null)

    fun getVideoInfo(
        videoIdType: String,
        videoId: String?,
        activity: Activity
    ) {
        viewModelScope.launch {
            if (videoId.isNullOrEmpty()) {
                state = state.copy(uiState = UIState.Failed)
                return@launch
            }
            val response =
                VideoInfo.getVideoDetailedInfoByIdWeb(videoIdType, videoId)
            if (response.code != 0 || response.data?.data == null || response.data?.code != 0) {
                state = state.copy(uiState = UIState.Failed)
                return@launch
            }
            response.data?.data?.view?.redirectUrl?.let { url ->
                val uri = Uri.parse(url)
                val epid = uri.path?.replace("/bangumi/play/", "")
                if (epid?.startsWith("ep") == true) {
                    val epidLong = epid.replace("ep", "").toLong()
                    activity.startActivity(Intent(activity, BangumiActivity::class.java).apply {
                        putExtra(PARAM_BANGUMI_ID_TYPE, BANGUMI_ID_TYPE_EPID)
                        putExtra(PARAM_BANGUMI_ID, epidLong)
                    })
                    activity.finish()
                }
            }
            listOf(
                viewModelScope.async {
                    getVideoSanLianState(videoIdType, videoId)
                },
                viewModelScope.async {
                    getImageBitmap(
                        response.data?.data?.view?.pic?.replace("http://", "https://") ?: ""
                    )
                }
            ).awaitAll()
            state = state.copy(uiState = UIState.Success, videoData = response.data?.data)
        }
    }

    private suspend fun getVideoSanLianState(
        videoIdType: String,
        videoId: String?
    ) {    //三连有英文吗？
        val tasks = listOf(
            viewModelScope.async {
                isLiked(videoIdType, videoId)
            },
            viewModelScope.async {
                isCoined(videoIdType, videoId)
            },
            viewModelScope.async {
                isFav(videoId)
            }
        )
        tasks.awaitAll()
    }

    private suspend fun isLiked(
        videoIdType: String,
        videoId: String?
    ) {
        isLiked = VideoInfo.isLiked(
            videoIdType,
            videoId
        )
    }

    private suspend fun isCoined(
        videoIdType: String,
        videoId: String?
    ) {
        isCoined = VideoInfo.isCoined(
            videoIdType,
            videoId
        )
    }

    /**
     * info screen fav checking required
     */
    private suspend fun isFav(
        videoId: String?
    ) {
        isFav = VideoInfo.isFav(videoId)

    }

    fun likeVideo(
        videoIdType: String,
        videoId: String?
    ) {
        isLiked = !isLiked

        viewModelScope.launch {
            val response = VideoAction.likeVideo(videoIdType, videoId, !isLiked)
            if (response.code != 0) {
                isLiked = !isLiked

                ToastUtils.showText("${response.code}: ${response.message}")
            }
            isLiked(videoIdType, videoId)
            logd(response)
        }
    }

    fun addToWatchLater(
        videoIdType: String,
        videoId: String
    ) {
        viewModelScope.launch {
            val response = VideoAction.addToWatchLater(videoIdType, videoId)
            if (response.code != 0) {
                ToastUtils.showText("添加失败！${response.code}: ${response.message}")
            } else {
                ToastUtils.showText("添加成功！记得来看我哦～")
            }
        }
    }

    private fun getImageBitmap(url: String) {
        viewModelScope.launch {
            try {
                val response: HttpResponse = ktorNetworkUtils.client.get(url) {
                    //accept(ContentType.Application.Json)
                }
                if (response.status == HttpStatusCode.OK) {
                    val bytes = response.readBytes()
                    val bitmap = getBitmapFromBytes(
                        bytes,
                        //true
                    )
                    if (bitmap != null) {
                        imageBitmap = bitmap
                        //Log.d("TAG", "getImageBitmap: $url success")
                    }
                } else {
                    MainScope().launch {
                        ToastUtils.showText("${response.status.value}")
                    }
                }
            } catch (e: Exception) {
                //getImageBitmap(url, onSuccess)
                e.printStackTrace()
            }
        }
    }

    @Throws(FileNotFoundException::class, IOException::class)
    fun getBitmapFromBytes(bytes: ByteArray/*, needCompress: Boolean*/): Bitmap? {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        /*if (needCompress) {
            val onlyBoundsOptions: BitmapFactory.Options = BitmapFactory.Options()
            onlyBoundsOptions.inJustDecodeBounds = true
            //onlyBoundsOptions.inDither = true //optional
            onlyBoundsOptions.inPreferredConfig = Bitmap.Config.ARGB_8888 //optional
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val originalWidth = onlyBoundsOptions.outWidth
            val originalHeight = onlyBoundsOptions.outHeight
            if ((originalWidth == -1) || (originalHeight == -1)) return null
            //图片分辨率以480x800为标准
            val hh = 128f //这里设置高度为800f
            val ww = 128f //这里设置宽度为480f
            //缩放比。由于是固定比例缩放，只用高或者宽其中一个数据进行计算即可
            var be = 1 //be=1表示不缩放
            be = if (originalWidth > originalHeight && originalWidth > ww) {
                (originalWidth / ww).toInt()
            } else if (originalWidth < originalHeight && originalHeight > hh) {
                (originalHeight / hh).toInt()
            } else 1

            //比例压缩
            val bitmapOptions: BitmapFactory.Options = BitmapFactory.Options()
            bitmapOptions.inSampleSize = be //设置缩放比例
            //bitmapOptions.inDither = true //optional
            bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888 //optional
            //input = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            //input?.close()
            return compressImage(bitmap!!) //再进行质量压缩
        } else {
            return
        }*/
    }
}