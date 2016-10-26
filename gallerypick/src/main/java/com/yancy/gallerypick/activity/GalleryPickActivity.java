package com.yancy.gallerypick.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.yancy.gallerypick.R;
import com.yancy.gallerypick.adapter.PhotoAdapter;
import com.yancy.gallerypick.bean.FolderInfo;
import com.yancy.gallerypick.bean.PhotoInfo;
import com.yancy.gallerypick.config.GalleryConfig;
import com.yancy.gallerypick.config.GalleryPick;
import com.yancy.gallerypick.inter.IHandlerCallBack;
import com.yancy.gallerypick.utils.FileUtils;
import com.yancy.gallerypick.utils.UIUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片选择页面
 * Created by Yancy on 2016/1/26.
 */
public class GalleryPickActivity extends BaseActivity {

    private Context mContext = null;
    private Activity mActivity = null;
    private final static String TAG = "GalleryPickActivity";

    private ArrayList<String> resultPhoto;

    private TextView tvFinish;                  // 完成按钮
    private LinearLayout btnGalleryPickBack;    // 返回按钮
    private RecyclerView rvGalleryImage;        // 图片列表

    private PhotoAdapter photoAdapter;              // 图片适配器

    private List<FolderInfo> folderInfoList = new ArrayList<>();    // 本地文件夹信息List
    private List<PhotoInfo> photoInfoList = new ArrayList<>();      // 本地图片信息List

    private static final int LOADER_ALL = 0;         // 获取所有图片
    private static final int LOADER_CATEGORY = 1;    // 获取某个文件夹中的所有图片

    private boolean hasFolderScan = false;           // 是否扫描过

    private GalleryConfig galleryConfig;   // GalleryPick 配置器

    private static final int REQUEST_CAMERA = 100;   // 设置拍摄照片的 REQUEST_CODE

    private IHandlerCallBack mHandlerCallBack;   // GalleryPick 生命周期接口


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gallery_main);

        mContext = this;
        mActivity = this;

        UIUtils.hideTitleBar(mActivity, R.id.ll_gallery_pick_main);

        initView();
        init();
        initPhoto();


    }

    /**
     * 初始化视图
     */
    private void initView() {
        tvFinish = (TextView) super.findViewById(R.id.tvFinish);
        btnGalleryPickBack = (LinearLayout) super.findViewById(R.id.btnGalleryPickBack);
        rvGalleryImage = (RecyclerView) super.findViewById(R.id.rvGalleryImage);
    }

    /**
     * 初始化
     */
    private void init() {
        galleryConfig = GalleryPick.getInstance().getGalleryConfig();
        mHandlerCallBack = galleryConfig.getIHandlerCallBack();
        mHandlerCallBack.onStart();

        resultPhoto = galleryConfig.getPathList();

        tvFinish.setText(getString(R.string.gallery_finish, resultPhoto.size(), galleryConfig.getMaxSize()));

        btnGalleryPickBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                mHandlerCallBack.onCancel();
                exit();
            }
        });

        GridLayoutManager gridLayoutManager = new GridLayoutManager(mContext, 3);
        rvGalleryImage.setLayoutManager(gridLayoutManager);
        photoAdapter = new PhotoAdapter(mActivity, mContext, photoInfoList);
        photoAdapter.setOnCallBack(new PhotoAdapter.OnCallBack() {
            @Override
            public void OnClickCamera(List<String> selectPhotoList) {
                resultPhoto.clear();
                resultPhoto.addAll(selectPhotoList);
                showCameraAction();
            }

            @Override
            public void OnClickPhoto(List<String> selectPhotoList) {
                tvFinish.setText(getString(R.string.gallery_finish, selectPhotoList.size(), galleryConfig.getMaxSize()));

                resultPhoto.clear();
                resultPhoto.addAll(selectPhotoList);

                if (!galleryConfig.isMultiSelect()) {
                    if (resultPhoto != null && resultPhoto.size() > 0) {
                        mHandlerCallBack.onSuccess(resultPhoto);
                        exit();
                    }
                }

            }

        });
        photoAdapter.setSelectPhoto(resultPhoto);
        rvGalleryImage.setAdapter(photoAdapter);


        if (!galleryConfig.isMultiSelect()) {
            tvFinish.setVisibility(View.GONE);
        }


        tvFinish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (resultPhoto != null && resultPhoto.size() > 0) {
                    mHandlerCallBack.onSuccess(resultPhoto);
                    exit();
                }

            }
        });


    }

    /**
     * 初始化配置
     */
    private void initPhoto() {

        LoaderManager.LoaderCallbacks<Cursor> mLoaderCallback = new LoaderManager.LoaderCallbacks<Cursor>() {

            private final String[] IMAGE_PROJECTION = {
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.SIZE
            };

            @Override
            public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                if (id == LOADER_ALL) {
                    return new CursorLoader(mActivity, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, IMAGE_PROJECTION, null, null, IMAGE_PROJECTION[2] + " DESC");
                } else if (id == LOADER_CATEGORY) {
                    return new CursorLoader(mActivity, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, IMAGE_PROJECTION, IMAGE_PROJECTION[0] + " like '%" + args.getString("path") + "%'", null, IMAGE_PROJECTION[2] + " DESC");
                }

                return null;
            }

            @Override
            public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
                if (data != null) {
                    int count = data.getCount();
                    if (count > 0) {
                        List<PhotoInfo> tempPhotoList = new ArrayList<>();
                        data.moveToFirst();
                        do {
                            String path = data.getString(data.getColumnIndexOrThrow(IMAGE_PROJECTION[0]));
                            String name = data.getString(data.getColumnIndexOrThrow(IMAGE_PROJECTION[1]));
                            long dateTime = data.getLong(data.getColumnIndexOrThrow(IMAGE_PROJECTION[2]));
                            int size = data.getInt(data.getColumnIndexOrThrow(IMAGE_PROJECTION[4]));
                            boolean showFlag = size > 1024 * 5;                           //是否大于5K
                            PhotoInfo photoInfo = new PhotoInfo(path, name, dateTime);
                            if (showFlag) {
                                tempPhotoList.add(photoInfo);
                            }
                            if (!hasFolderScan && showFlag) {
                                File photoFile = new File(path);                  // 获取图片文件
                                File folderFile = photoFile.getParentFile();      // 获取图片上一级文件夹

                                FolderInfo folderInfo = new FolderInfo();
                                folderInfo.name = folderFile.getName();
                                folderInfo.path = folderFile.getAbsolutePath();
                                folderInfo.photoInfo = photoInfo;
                                if (!folderInfoList.contains(folderInfo)) {      // 判断是否是已经扫描到的图片文件夹
                                    List<PhotoInfo> photoInfoList = new ArrayList<>();
                                    photoInfoList.add(photoInfo);
                                    folderInfo.photoInfoList = photoInfoList;
                                    folderInfoList.add(folderInfo);
                                } else {
                                    FolderInfo f = folderInfoList.get(folderInfoList.indexOf(folderInfo));
                                    f.photoInfoList.add(photoInfo);
                                }
                            }

                        } while (data.moveToNext());

                        photoInfoList.clear();
                        photoInfoList.addAll(tempPhotoList);
                        photoAdapter.notifyDataSetChanged();

                        hasFolderScan = true;
                    }
                }
            }

            @Override
            public void onLoaderReset(Loader<Cursor> loader) {

            }
        };
        getSupportLoaderManager().restartLoader(LOADER_ALL, null, mLoaderCallback);   // 扫描手机中的图片
    }


    private File tempFile;

    /**
     * 选择相机
     */
    private void showCameraAction() {
        // 跳转到系统照相机
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(mActivity.getPackageManager()) != null) {
            // 设置系统相机拍照后的输出路径
            // 创建临时文件
            tempFile = FileUtils.createTmpFile(mActivity, galleryConfig.getFilePath());
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(tempFile));
            startActivityForResult(cameraIntent, REQUEST_CAMERA);
        } else {
            Toast.makeText(mContext, R.string.gallery_msg_no_camera, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CAMERA) {
            if (resultCode == Activity.RESULT_OK) {
                if (tempFile != null) {
                    if (!galleryConfig.isMultiSelect()) {
                        resultPhoto.clear();
                    }
                    resultPhoto.add(tempFile.getAbsolutePath());
                    mHandlerCallBack.onSuccess(resultPhoto);
                    exit();
                }
            } else {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * 退出
     */
    private void exit() {
        mHandlerCallBack.onFinish();
        finish();
    }

    /**
     * 回退键监听
     */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            mHandlerCallBack.onCancel();
            exit();
        }
        return true;
    }


}
/*
 *   ┏┓　　　┏┓
 * ┏┛┻━━━┛┻┓
 * ┃　　　　　　　┃
 * ┃　　　━　　　┃
 * ┃　┳┛　┗┳　┃
 * ┃　　　　　　　┃
 * ┃　　　┻　　　┃
 * ┃　　　　　　　┃
 * ┗━┓　　　┏━┛
 *     ┃　　　┃
 *     ┃　　　┃
 *     ┃　　　┗━━━┓
 *     ┃　　　　　　　┣┓
 *     ┃　　　　　　　┏┛
 *     ┗┓┓┏━┳┓┏┛
 *       ┃┫┫　┃┫┫
 *       ┗┻┛　┗┻┛
 *        神兽保佑
 *        代码无BUG!
 */