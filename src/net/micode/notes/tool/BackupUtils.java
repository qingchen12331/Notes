/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.tool;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 * BackupUtils 类用于实现笔记数据的导出功能。
 * 它将笔记数据导出为文本文件，存储到外部存储设备（如 SD 卡）中。
 * 该类采用单例模式，确保全局只有一个实例。
 */
public class BackupUtils {
    private static final String TAG = "BackupUtils";
    // 单例模式相关
    private static BackupUtils sInstance;

    /**
     * 获取 BackupUtils 的单例实例。
     *
     * @param context 应用上下文
     * @return BackupUtils 实例
     */
    public static synchronized BackupUtils getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BackupUtils(context);
        }
        return sInstance;
    }

    /**
     * 定义备份和恢复状态的常量。
     */
    // SD 卡未挂载
    public static final int STATE_SD_CARD_UNMOUONTED = 0;
    // 备份文件不存在
    public static final int STATE_BACKUP_FILE_NOT_EXIST = 1;
    // 数据格式损坏
    public static final int STATE_DATA_DESTROIED = 2;
    // 系统错误
    public static final int STATE_SYSTEM_ERROR = 3;
    // 操作成功
    public static final int STATE_SUCCESS = 4;

    private TextExport mTextExport;

    private BackupUtils(Context context) {
        mTextExport = new TextExport(context);
    }

    /**
     * 检查外部存储是否可用。
     *
     * @return 是否可用
     */
    private static boolean externalStorageAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * 将笔记数据导出为文本文件。
     *
     * @return 导出操作的状态码
     */
    public int exportToText() {
        return mTextExport.exportToText();
    }

    /**
     * 获取导出的文本文件名。
     *
     * @return 文件名
     */
    public String getExportedTextFileName() {
        return mTextExport.mFileName;
    }

    /**
     * 获取导出的文本文件目录。
     *
     * @return 文件目录
     */
    public String getExportedTextFileDir() {
        return mTextExport.mFileDirectory;
    }

    /**
     * 内部类 TextExport，用于实现具体的导出逻辑。
     */
    private static class TextExport {
        private static final String[] NOTE_PROJECTION = {
                NoteColumns.ID,               // 笔记 ID
                NoteColumns.MODIFIED_DATE,    // 修改日期
                NoteColumns.SNIPPET,          // 摘要
                NoteColumns.TYPE              // 笔记类型
        };

        private static final int NOTE_COLUMN_ID = 0;
        private static final int NOTE_COLUMN_MODIFIED_DATE = 1;
        private static final int NOTE_COLUMN_SNIPPET = 2;

        private static final String[] DATA_PROJECTION = {
                DataColumns.CONTENT,          // 数据内容
                DataColumns.MIME_TYPE,        // 数据类型
                DataColumns.DATA1,            // 数据字段 1
                DataColumns.DATA2,            // 数据字段 2
                DataColumns.DATA3,            // 数据字段 3
                DataColumns.DATA4             // 数据字段 4
        };

        private static final int DATA_COLUMN_CONTENT = 0;
        private static final int DATA_COLUMN_MIME_TYPE = 1;
        private static final int DATA_COLUMN_CALL_DATE = 2;
        private static final int DATA_COLUMN_PHONE_NUMBER = 4;

        private final String[] TEXT_FORMAT;   // 导出文本的格式化字符串
        private static final int FORMAT_FOLDER_NAME = 0;    // 文件夹名称格式
        private static final int FORMAT_NOTE_DATE = 1;      // 笔记日期格式
        private static final int FORMAT_NOTE_CONTENT = 2;   // 笔记内容格式

        private Context mContext;
        private String mFileName;             // 导出文件名
        private String mFileDirectory;        // 导出文件目录

        /**
         * 构造函数，初始化导出工具。
         *
         * @param context 应用上下文
         */
        public TextExport(Context context) {
            TEXT_FORMAT = context.getResources().getStringArray(R.array.format_for_exported_note);
            mContext = context;
            mFileName = "";
            mFileDirectory = "";
        }

        /**
         * 获取格式化字符串。
         *
         * @param id 格式化字符串的索引
         * @return 格式化字符串
         */
        private String getFormat(int id) {
            return TEXT_FORMAT[id];
        }

        /**
         * 将指定文件夹及其笔记导出为文本。
         *
         * @param folderId 文件夹 ID
         * @param ps       输出流
         */
        private void exportFolderToText(String folderId, PrintStream ps) {
            // 查询属于该文件夹的笔记
            Cursor notesCursor = mContext.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION, NoteColumns.PARENT_ID + "=?", new String[]{folderId}, null);

            if (notesCursor != null) {
                if (notesCursor.moveToFirst()) {
                    do {
                        // 打印笔记的最后修改日期
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                notesCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // 查询属于该笔记的数据
                        String noteId = notesCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (notesCursor.moveToNext());
                }
                notesCursor.close();
            }
        }

        /**
         * 将指定笔记导出为文本。
         *
         * @param noteId 笔记 ID
         * @param ps     输出流
         */
        private void exportNoteToText(String noteId, PrintStream ps) {
            Cursor dataCursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI,
                    DATA_PROJECTION, DataColumns.NOTE_ID + "=?", new String[]{noteId}, null);

            if (dataCursor != null) {
                if (dataCursor.moveToFirst()) {
                    do {
                        String mimeType = dataCursor.getString(DATA_COLUMN_MIME_TYPE);
                        if (DataConstants.CALL_NOTE.equals(mimeType)) {
                            // 打印电话号码
                            String phoneNumber = dataCursor.getString(DATA_COLUMN_PHONE_NUMBER);
                            long callDate = dataCursor.getLong(DATA_COLUMN_CALL_DATE);
                            String location = dataCursor.getString(DATA_COLUMN_CONTENT);

                            if (!TextUtils.isEmpty(phoneNumber)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), phoneNumber));
                            }
                            // 打印通话日期
                            ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), DateFormat
                                    .format(mContext.getString(R.string.format_datetime_mdhm), callDate)));
                            // 打印通话附件位置
                            if (!TextUtils.isEmpty(location)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), location));
                            }
                        } else if (DataConstants.NOTE.equals(mimeType)) {
                            String content = dataCursor.getString(DATA_COLUMN_CONTENT);
                            if (!TextUtils.isEmpty(content)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), content));
                            }
                        }
                    } while (dataCursor.moveToNext());
                }
                dataCursor.close();
            }
            // 在笔记之间打印分隔符
            try {
                ps.write(new byte[]{Character.LINE_SEPARATOR, Character.LETTER_NUMBER});
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

        /**
         * 将笔记数据导出为可读的文本文件。
         *
         * @return 导出操作的状态码
         */
        public int exportToText() {
            if (!externalStorageAvailable()) {
                Log.d(TAG, "Media was not mounted");
                return STATE_SD_CARD_UNMOUONTED;
            }

            PrintStream ps = getExportToTextPrintStream();
            if (ps == null) {
                Log.e(TAG, "get print stream error");
                return STATE_SYSTEM PrintStream ps = getExportToTextPrintStream();
                if (ps == null) {
                    Log.e(TAG, "get print stream error");
                    return STATE_SYSTEM_ERROR;
                }

                // 首先导出文件夹及其笔记
                Cursor folderCursor = mContext.getContentResolver().query(
                        Notes.CONTENT_NOTE_URI,
                        NOTE_PROJECTION,
                        "(" + NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER + " AND "
                                + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLDER + ") OR "
                                + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER, null, null);

                if (folderCursor != null) {
                    if (folderCursor.moveToFirst()) {
                        do {
                            // 打印文件夹名称
                            String folderName = "";
                            if (folderCursor.getLong(NOTE_COLUMN_ID) == Notes.ID_CALL_RECORD_FOLDER) {
                                folderName = mContext.getString(R.string.call_record_folder_name);
                            } else {
                                folderName = folderCursor.getString(NOTE_COLUMN_SNIPPET);
                            }
                            if (!TextUtils.isEmpty(folderName)) {
                                ps.println(String.format(getFormat(FORMAT_FOLDER_NAME), folderName));
                            }
                            String folderId = folderCursor.getString(NOTE_COLUMN_ID);
                            exportFolderToText(folderId, ps); // 导出文件夹中的笔记
                        } while (folderCursor.moveToNext());
                    }
                    folderCursor.close();
                }

                // 导出根文件夹中的笔记
                Cursor noteCursor = mContext.getContentResolver().query(
                        Notes.CONTENT_NOTE_URI,
                        NOTE_PROJECTION,
                        NoteColumns.TYPE + "=" + Notes.TYPE_NOTE + " AND " + NoteColumns.PARENT_ID
                                + "=0", null, null);

                if (noteCursor != null) {
                    if (noteCursor.moveToFirst()) {
                        do {
                            ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                    mContext.getString(R.string.format_datetime_mdhm),
                                    noteCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                            // 查询属于该笔记的数据
                            String noteId = noteCursor.getString(NOTE_COLUMN_ID);
                            exportNoteToText(noteId, ps);
                        } while (noteCursor.moveToNext());
                    }
                    noteCursor.close();
                }
                ps.close();

                return STATE_SUCCESS;
            }

            /**
             * 获取指向导出文件的 PrintStream 对象。
             *
             * @return PrintStream 对象
             */
            private PrintStream getExportToTextPrintStream () {
                File file = generateFileMountedOnSDcard(mContext, R.string.file_path,
                        R.string.file_name_txt_format);
                if (file == null) {
                    Log.e(TAG, "create file to exported failed");
                    return null;
                }
                mFileName = file.getName();
                mFileDirectory = mContext.getString(R.string.file_path);
                PrintStream ps = null;
                try {
                    FileOutputStream fos = new FileOutputStream(file);
                    ps = new PrintStream(fos);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    return null;
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    return null;
                }
                return ps;
            }
        }

        /**
         * 在外部存储设备（如 SD 卡）上生成用于存储导出数据的文件。
         *
         * @param context             应用上下文
         * @param filePathResId       文件路径的资源 ID
         * @param fileNameFormatResId 文件名格式的资源 ID
         * @return 生成的文件对象
         */
        private static File generateFileMountedOnSDcard(Context context, int filePathResId, int fileNameFormatResId) {
            StringBuilder sb = new StringBuilder();
            sb.append(Environment.getExternalStorageDirectory()); // 获取 SD 卡根目录
            sb.append(context.getString(filePathResId)); // 拼接文件路径
            File filedir = new File(sb.toString());
            sb.append(context.getString(
                    fileNameFormatResId,
                    DateFormat.format(context.getString(R.string.format_date_ymd),
                            System.currentTimeMillis()))); // 拼接文件名
            File file = new File(sb.toString());

            try {
                if (!filedir.exists()) {
                    filedir.mkdir(); // 创建文件夹
                }
                if (!file.exists()) {
                    file.createNewFile(); // 创建文件
                }
                return file;
            } catch (SecurityException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }
    }
}