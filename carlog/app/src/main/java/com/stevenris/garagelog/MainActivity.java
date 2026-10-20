package com.stevenris.garagelog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int EXPORT = 71, IMPORT = 72;
    private Db db;
    private LinearLayout page;
    private long carId = -1;
    private final int ink = Color.rgb(27, 38, 48), muted = Color.rgb(92, 105, 116);
    private final int blue = Color.rgb(26, 102, 187);

    static class Db extends SQLiteOpenHelper {
        Db(Activity a) { super(a, "garage.db", null, 1); }
        @Override public void onCreate(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE cars (_id INTEGER PRIMARY KEY, name TEXT NOT NULL, odo INTEGER NOT NULL)");
            d.execSQL("CREATE TABLE items (_id INTEGER PRIMARY KEY, car INTEGER NOT NULL, name TEXT NOT NULL, interval_km INTEGER NOT NULL, last_km INTEGER NOT NULL)");
            d.execSQL("CREATE TABLE services (_id INTEGER PRIMARY KEY, item INTEGER NOT NULL, odo INTEGER NOT NULL, stamp INTEGER NOT NULL)");
            d.execSQL("CREATE TABLE fills (_id INTEGER PRIMARY KEY, car INTEGER NOT NULL, odo INTEGER NOT NULL, liters REAL NOT NULL, cost REAL NOT NULL, full INTEGER NOT NULL, stamp INTEGER NOT NULL)");
        }
        @Override public void onUpgrade(SQLiteDatabase d,int oldVersion,int newVersion) {}
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13, 50, 98));
        getWindow().setNavigationBarColor(Color.rgb(13, 50, 98));
        db = new Db(this);
        show();
    }
    private SQLiteDatabase data() { return db.getWritableDatabase(); }
    private int dip(float n) { return (int)(getResources().getDisplayMetrics().density*n+.5f); }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private void heading(String title, String subtitle) {
        TextView h = label(title, 25, ink, true); h.setPadding(0,dip(9),0,dip(4)); page.addView(h);
        if (subtitle != null) page.addView(label(subtitle,14,muted,false));
    }
    private TextView label(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if(bold) t.setTypeface(null,Typeface.BOLD);
        return t;
    }
    private Button button(String text, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextColor(blue);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private EditText field(String hint, int type) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setHint(hint); e.setInputType(type);
        return e;
    }
    private void card(LinearLayout content) {
        content.setPadding(dip(15),dip(12),dip(15),dip(12));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE); bg.setCornerRadius(dip(16));
        content.setBackground(bg); content.setElevation(dip(2));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dip(11),0,dip(2)); page.addView(content,p);
    }
    private void show() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(244,247,250));
        page = column(); page.setPadding(dip(18),dip(18),dip(18),dip(35));
        scroll.addView(page); setContentView(scroll);
        if(carId == -1) showCars(); else showCar();
    }
    private void showCars() {
        heading("Мой гараж", "Обслуживание и расход топлива");
        page.addView(button("＋ Добавить автомобиль",this::addCar));
        page.addView(button("Сохранить резервную копию",this::exportBackup));
        page.addView(button("Восстановить из копии",this::importBackup));
        try(Cursor c=data().rawQuery("SELECT _id,name,odo FROM cars ORDER BY _id",null)) {
            if(c.getCount()==0) page.addView(label("Добавь машину и её текущий пробег, чтобы начать.",16,muted,false));
            while(c.moveToNext()) {
                long id=c.getLong(0); LinearLayout box=column();
                box.addView(label(c.getString(1),20,ink,true));
                box.addView(label("Пробег: "+c.getLong(2)+" км",15,muted,false));
                box.addView(button("Открыть →",()->{carId=id;show();})); card(box);
            }
        }
    }
    private void addCar() {
        LinearLayout form=column(); form.setPadding(dip(15),0,dip(15),0);
        EditText name=field("Название: Honda Civic",InputType.TYPE_CLASS_TEXT);
        EditText km=field("Текущий пробег, км",InputType.TYPE_CLASS_NUMBER);
        form.addView(name);form.addView(km);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Новая машина").setView(form)
            .setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{
            Long odo=number(km);
            if(name.getText().toString().trim().isEmpty()||odo==null){invalid();return;}
            ContentValues cv=new ContentValues();cv.put("name",name.getText().toString().trim());cv.put("odo",odo);
            carId=data().insertOrThrow("cars",null,cv); dialog.dismiss();show();
        }));dialog.show();
    }
    private void showCar() {
        try(Cursor c=data().rawQuery("SELECT name,odo FROM cars WHERE _id=?",args(carId))) {
            if(!c.moveToFirst()){carId=-1;show();return;}
            heading(c.getString(0),"Текущий пробег: "+c.getLong(1)+" км");
        }
        page.addView(button("← Все машины",()->{carId=-1;show();}));
        page.addView(button("＋ Заправка",this::addFill));
        page.addView(button("＋ Расходник",this::addItem));
        page.addView(button("Исправить пробег",this::editOdo));
        page.addView(label("Расходники",19,ink,true));
        try(Cursor c=data().rawQuery("SELECT _id,name,interval_km,last_km FROM items WHERE car=? ORDER BY name",args(carId))) {
            if(c.getCount()==0)page.addView(label("Добавь масло, фильтры и другие расходники.",15,muted,false));
            while(c.moveToNext()) addItemCard(c.getLong(0),c.getString(1),c.getLong(2),c.getLong(3));
        }
        page.addView(label("Топливо",19,ink,true));
        addFuelSummary();
        page.addView(button("История заправок",this::fuelHistory));
    }
    private long odo() {
        try(Cursor c=data().rawQuery("SELECT odo FROM cars WHERE _id=?",args(carId))) {
            return c.moveToFirst()?c.getLong(0):0;
        }
    }
    private void addItemCard(long id,String name,long interval,long last) {
        long used=Math.max(0,odo()-last);
        long remain=Math.max(0,interval-used);
        int percent=(int)Math.max(0,Math.min(100,Math.round(remain*100.0/interval)));
        LinearLayout box=column();
        box.addView(label(name,18,ink,true));
        box.addView(label(percent+"% осталось · "+remain+" км до замены",15,percent<=15?Color.rgb(185,65,32):blue,true));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);bar.setProgress(percent);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(percent<=15?Color.rgb(218,88,47):blue));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dip(9));bp.setMargins(0,dip(9),0,dip(8));box.addView(bar,bp);
        box.addView(label("Заменено: "+last+" км · интервал: "+interval+" км",13,muted,false));
        box.addView(button("Отметить замену",()->replaceItem(id,name)));
        box.addView(button("Изменить / история",()->itemOptions(id,name)));
        card(box);
    }
    private void addItem() { itemForm(-1); }
    private void itemForm(long id) {
        LinearLayout form=column();form.setPadding(dip(15),0,dip(15),0);
        EditText name=field("Название: масло двигателя",InputType.TYPE_CLASS_TEXT);
        EditText interval=field("Интервал замены, км: 10000",InputType.TYPE_CLASS_NUMBER);
        EditText last=field("Пробег последней замены, км",InputType.TYPE_CLASS_NUMBER);
        if(id!=-1)try(Cursor c=data().rawQuery("SELECT name,interval_km,last_km FROM items WHERE _id=?",args(id))) {
            if(c.moveToFirst()){name.setText(c.getString(0));interval.setText(c.getString(1));last.setText(c.getString(2));}
        }
        form.addView(name);form.addView(interval);form.addView(last);
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle(id==-1?"Новый расходник":"Изменить расходник")
            .setView(form).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dlg.setOnShowListener(v->dlg.getButton(-1).setOnClickListener(w->{
            Long i=number(interval),l=number(last);
            if(name.getText().toString().trim().isEmpty()||i==null||i<1||l==null||l>odo()){invalid();return;}
            ContentValues cv=new ContentValues();cv.put("name",name.getText().toString().trim());
            cv.put("interval_km",i);cv.put("last_km",l);cv.put("car",carId);
            if(id==-1)data().insertOrThrow("items",null,cv);else data().update("items",cv,"_id=?",args(id));
            dlg.dismiss();show();
        }));dlg.show();
    }
    private void replaceItem(long id,String name) {
        EditText km=field("Пробег при замене, км",InputType.TYPE_CLASS_NUMBER);
        km.setText(String.valueOf(odo()));
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Заменил: "+name)
            .setMessage("Укажи пробег автомобиля при замене.")
            .setView(km).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dlg.setOnShowListener(v->dlg.getButton(-1).setOnClickListener(w->{
            Long value=number(km);if(value==null||value<0){invalid();return;}
            SQLiteDatabase d=data();d.beginTransaction();
            try {
                ContentValues cv=new ContentValues();cv.put("last_km",value);
                d.update("items",cv,"_id=?",args(id));
                ContentValues log=new ContentValues();log.put("item",id);log.put("odo",value);log.put("stamp",System.currentTimeMillis());
                d.insertOrThrow("services",null,log);
                if(value>odo()){cv=new ContentValues();cv.put("odo",value);d.update("cars",cv,"_id=?",args(carId));}
                d.setTransactionSuccessful();
            }finally{d.endTransaction();}
            dlg.dismiss();show();
        }));dlg.show();
    }
    private void itemOptions(long id,String name) {
        new AlertDialog.Builder(this).setTitle(name)
            .setItems(new String[]{"Изменить параметры","История замен","Удалить расходник"},(dialog,which)->{
                if(which==0)itemForm(id);
                if(which==1){
                    StringBuilder sb=new StringBuilder();
                    try(Cursor c=data().rawQuery("SELECT odo,stamp FROM services WHERE item=? ORDER BY stamp DESC,_id DESC",args(id))) {
                        while(c.moveToNext())sb.append(date(c.getLong(1))).append(" · ").append(c.getLong(0)).append(" км\n");
                    }
                    new AlertDialog.Builder(this).setTitle("История: "+name)
                        .setMessage(sb.length()==0?"Замен пока нет":sb.toString()).setPositiveButton("Готово",null).show();
                }
                if(which==2)new AlertDialog.Builder(this).setTitle("Удалить "+name+"?")
                    .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{
                        data().delete("services","item=?",args(id));data().delete("items","_id=?",args(id));show();
                    }).show();
            }).show();
    }
    private void editOdo() {
        EditText km=field("Текущий пробег, км",InputType.TYPE_CLASS_NUMBER);
        km.setText(String.valueOf(odo()));
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Исправить пробег")
            .setView(km).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dlg.setOnShowListener(v->dlg.getButton(-1).setOnClickListener(w->{
            Long value=number(km);if(value==null){invalid();return;}
            ContentValues cv=new ContentValues();cv.put("odo",value);
            data().update("cars",cv,"_id=?",args(carId));dlg.dismiss();show();
        }));dlg.show();
    }
    private void addFill() {
        LinearLayout form=column();form.setPadding(dip(15),0,dip(15),0);
        EditText km=field("Пробег, км",InputType.TYPE_CLASS_NUMBER);km.setText(String.valueOf(odo()));
        EditText liters=field("Залито литров, например 35,5",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText cost=field("Стоимость всей заправки, zł (необязательно)",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        CheckBox full=new CheckBox(this);full.setText("Бак заправлен до полного");full.setChecked(true);
        form.addView(km);form.addView(liters);form.addView(cost);form.addView(full);
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Новая заправка")
            .setView(form).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dlg.setOnShowListener(v->dlg.getButton(-1).setOnClickListener(w->{
            Long distance=number(km);Double amount=decimal(liters),price=cost.getText().toString().trim().isEmpty()?0:decimal(cost);
            long latest=-1;
            try(Cursor c=data().rawQuery("SELECT MAX(odo) FROM fills WHERE car=?",args(carId))) {if(c.moveToFirst()&&!c.isNull(0))latest=c.getLong(0);}
            if(distance==null||distance<odo()||distance<=latest||amount==null||amount<=0||price==null||price<0){invalid();return;}
            SQLiteDatabase d=data();d.beginTransaction();
            try {
                ContentValues cv=new ContentValues();cv.put("car",carId);cv.put("odo",distance);
                cv.put("liters",amount);cv.put("cost",price);cv.put("full",full.isChecked()?1:0);
                cv.put("stamp",System.currentTimeMillis());d.insertOrThrow("fills",null,cv);
                cv=new ContentValues();cv.put("odo",distance);d.update("cars",cv,"_id=?",args(carId));
                d.setTransactionSuccessful();
            }finally{d.endTransaction();}
            dlg.dismiss();show();
        }));dlg.show();
    }
    private void addFuelSummary() {
        long from=-1;double liters=0,totalLiters=0;long totalKm=0;
        double latest=0;long latestDistance=0;
        try(Cursor c=data().rawQuery("SELECT odo,liters,full FROM fills WHERE car=? ORDER BY odo,_id",args(carId))) {
            while(c.moveToNext()) {
                long distance=c.getLong(0);double amount=c.getDouble(1);boolean full=c.getInt(2)==1;
                if(from!=-1)liters+=amount;
                if(full){
                    if(from!=-1&&distance>from){
                        latest=liters*100/(distance-from);latestDistance=distance-from;
                        totalLiters+=liters;totalKm+=distance-from;
                    }
                    from=distance;liters=0;
                }
            }
        }
        LinearLayout box=column();
        if(latestDistance>0){
            box.addView(label(String.format(Locale.getDefault(),"%.1f л/100 км",latest),23,blue,true));
            box.addView(label("Последний отрезок: "+latestDistance+" км",14,muted,false));
            box.addView(label(String.format(Locale.getDefault(),"Средний по полным бакам: %.1f л/100 км",totalLiters*100/totalKm),14,ink,false));
        }else box.addView(label("Для расчёта расхода нужны две заправки до полного бака.",14,muted,false));
        card(box);
    }
    private void fuelHistory() {
        StringBuilder sb=new StringBuilder();
        try(Cursor c=data().rawQuery("SELECT odo,liters,cost,full,stamp FROM fills WHERE car=? ORDER BY odo DESC,_id DESC LIMIT 40",args(carId))) {
            while(c.moveToNext()){
                sb.append(date(c.getLong(4))).append(" · ").append(c.getLong(0)).append(" км\n");
                sb.append(String.format(Locale.getDefault(),"%.2f л · %.2f zł",c.getDouble(1),c.getDouble(2)));
                if(c.getInt(3)==1)sb.append(" · полный бак");
                sb.append("\n\n");
            }
        }
        new AlertDialog.Builder(this).setTitle("Заправки")
            .setMessage(sb.length()==0?"Заправок пока нет":sb.toString())
            .setPositiveButton("Готово",null).show();
    }
    private String date(long stamp){return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(stamp));}
    private String[] args(long n){return new String[]{String.valueOf(n)};}
    private Long number(EditText e){
        try { long v=Long.parseLong(e.getText().toString().trim());return v>=0?v:null; }
        catch(Exception ignored){return null;}
    }
    private Double decimal(EditText e){
        try { double v=Double.parseDouble(e.getText().toString().trim().replace(',','.'));return Double.isFinite(v)?v:null; }
        catch(Exception ignored){return null;}
    }
    private void invalid(){Toast.makeText(this,"Проверь поля и пробег. Новая заправка должна быть позже предыдущей.",Toast.LENGTH_LONG).show();}
    private void exportBackup(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE,"garage-backup.json");startActivityForResult(i,EXPORT);
    }
    private void importBackup(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");startActivityForResult(i,IMPORT);
    }
    @Override protected void onActivityResult(int request,int result,Intent intent){
        super.onActivityResult(request,result,intent);
        if(result!=RESULT_OK||intent==null||intent.getData()==null)return;
        Uri uri=intent.getData();
        try {
            if(request==EXPORT){
                JSONObject root=new JSONObject();root.put("format",1);
                for(String table:new String[]{"cars","items","services","fills"}){
                    JSONArray records=new JSONArray();
                    try(Cursor c=data().rawQuery("SELECT * FROM "+table,null)){
                        while(c.moveToNext()){
                            JSONObject row=new JSONObject();
                            for(int j=0;j<c.getColumnCount();j++){
                                String key=c.getColumnName(j);
                                if(c.isNull(j))row.put(key,JSONObject.NULL);
                                else if(c.getType(j)==Cursor.FIELD_TYPE_INTEGER)row.put(key,c.getLong(j));
                                else if(c.getType(j)==Cursor.FIELD_TYPE_FLOAT)row.put(key,c.getDouble(j));
                                else row.put(key,c.getString(j));
                            }
                            records.put(row);
                        }
                    }
                    root.put(table,records);
                }
                try(OutputStream out=getContentResolver().openOutputStream(uri)){
                    if(out==null)throw new Exception("Нет доступа к файлу");
                    out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this,"Резервная копия сохранена",Toast.LENGTH_LONG).show();
            }else if(request==IMPORT){
                byte[] bytes;
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    if(in==null)throw new Exception("Нет доступа к файлу");
                    ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                    byte[] chunk=new byte[8192];int n;
                    while((n=in.read(chunk))!=-1){
                        if(buffer.size()+n>10_000_000)throw new Exception("Слишком большой файл");
                        buffer.write(chunk,0,n);
                    }
                    bytes=buffer.toByteArray();
                }
                if(bytes.length>10_000_000)throw new Exception("Слишком большой файл");
                JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
                if(root.getInt("format")!=1)throw new Exception("Неподдерживаемый формат");
                String[] tables={"cars","items","services","fills"};
                for(String table:tables)root.getJSONArray(table);
                new AlertDialog.Builder(this).setTitle("Восстановить данные?")
                    .setMessage("Все нынешние машины и записи будут заменены содержимым копии.")
                    .setNegativeButton("Отмена",null)
                    .setPositiveButton("Восстановить",(d,w)->{
                        SQLiteDatabase sql=data();sql.beginTransaction();
                        try{
                            for(int t=tables.length-1;t>=0;t--)sql.delete(tables[t],null,null);
                            for(String table:tables){
                                JSONArray rows=root.getJSONArray(table);
                                if(rows.length()>100000)throw new Exception("Слишком много записей");
                                for(int j=0;j<rows.length();j++){
                                    JSONObject row=rows.getJSONObject(j);ContentValues cv=new ContentValues();
                                    java.util.Iterator<String> keys=row.keys();
                                    while(keys.hasNext()){
                                        String key=keys.next();
                                        if(!allowed(table,key))throw new Exception("Неизвестное поле");
                                        Object val=row.get(key);
                                        if(val instanceof Integer||val instanceof Long)cv.put(key,((Number)val).longValue());
                                        else if(val instanceof Double)cv.put(key,((Number)val).doubleValue());
                                        else if(val instanceof String)cv.put(key,(String)val);
                                        else throw new Exception("Некорректный формат");
                                    }
                                    sql.insertOrThrow(table,null,cv);
                                }
                            }
                            sql.setTransactionSuccessful();carId=-1;
                            Toast.makeText(this,"Данные восстановлены",Toast.LENGTH_LONG).show();
                        }catch(Exception ex){Toast.makeText(this,"Ошибка: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
                        finally{sql.endTransaction();show();}
                    }).show();
            }
        }catch(Exception e){Toast.makeText(this,"Ошибка: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private boolean allowed(String table,String key){
        switch(table){
            case "cars": return "_id".equals(key)||"name".equals(key)||"odo".equals(key);
            case "items": return "_id".equals(key)||"car".equals(key)||"name".equals(key)||"interval_km".equals(key)||"last_km".equals(key);
            case "services": return "_id".equals(key)||"item".equals(key)||"odo".equals(key)||"stamp".equals(key);
            case "fills": return "_id".equals(key)||"car".equals(key)||"odo".equals(key)||"liters".equals(key)||"cost".equals(key)||"full".equals(key)||"stamp".equals(key);
            default:return false;
        }
    }
    @Override public void onBackPressed(){if(carId!=-1){carId=-1;show();}else super.onBackPressed();}
}
