package jp.tapmarker;
import android.app.Activity;
import android.accessibilityservice.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Explicit local loopback: counts received contacts, not just injection callbacks. */
public final class InputCheckActivity extends Activity {
 final Handler handler=new Handler(Looper.getMainLooper());TapService service;TextView status;View pad;Button start;
 int ticket,requested,completed,downs,ups,cancelled;boolean testing,controlsHidden;float x,y,expectedX,expectedY;float[] points;long began;
 final java.util.Random random=new java.util.Random();
 int panelVisibility=View.VISIBLE;int[] markerVisibility;
 public void onCreate(Bundle saved){
  super.onCreate(saved);service=TapService.instance;
  LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(24,48,24,24);setContentView(layout);
  TextView title=new UiStrings.DisplayText(this);title.setText("タップの受信確認");title.setTextSize(24);layout.addView(title);
  TextView help=new UiStrings.DisplayText(this);help.setText("この画面に10回タップを送り、押す・離すが届いた回数を確認します。確認中は画面に触らずにお待ちください。ゲームの反応とは別の検査です。");help.setTextSize(16);layout.addView(help);
  status=new UiStrings.DisplayText(this);status.setTextSize(16);status.setPadding(0,20,0,20);layout.addView(status);
  start=new UiStrings.DisplayButton(this);start.setText("10回送って確認");start.setOnClickListener(v->begin());layout.addView(start);
  pad=new View(this){protected void onDraw(Canvas canvas){super.onDraw(canvas);Paint paint=new Paint(3);paint.setColor(0xffe7eef8);canvas.drawColor(0xffe7eef8);paint.setColor(0xff2866a9);canvas.drawCircle(getWidth()/2f,getHeight()/2f,28*getResources().getDisplayMetrics().density,paint);}public boolean onTouchEvent(android.view.MotionEvent event){if(testing&&event.getDownTime()>=began&&Math.abs(event.getRawX()-expectedX)<=2&&Math.abs(event.getRawY()-expectedY)<=2){if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)downs++;else if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP)ups++;else if(event.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL)cancelled++;}return true;}};
  layout.addView(pad,new LinearLayout.LayoutParams(-1,0,1));
  if(service==null){status.setText("ユーザー補助を有効にして、この画面を開き直してください。");start.setEnabled(false);}
  else{
   service.stop("入力の受信確認を開始");service.contacts().refresh();
   hideControls();
   status.setText(service.contacts().describe());
  }
 }
 void begin(){
  if(testing||service!=TapService.instance||!hasWindowFocus()||pad.getWidth()<=0||pad.getHeight()<=0)return;
  testing=true;requested=completed=downs=ups=cancelled=0;start.setEnabled(false);int current=++ticket;
  int[] location=new int[2];pad.getLocationOnScreen(location);x=location[0]+pad.getWidth()/2f;y=location[1]+pad.getHeight()/2f;
  // Let the physical press of the start button finish before injecting anything.
  points=service.mainTapPoints(new float[]{x,y},new int[]{location[0],location[1],location[0]+pad.getWidth(),location[1]+pad.getHeight()},Display.DEFAULT_DISPLAY);
  handler.postDelayed(()->{if(current!=ticket)return;began=SystemClock.uptimeMillis();send(current);},350);
 }
 void send(int current){
  if(current!=ticket||!testing)return;
  if(service!=TapService.instance||!hasWindowFocus()){finishCheck("画面が変わったため中断");return;}
  if(service.busy||service.gestures.busy()){if(SystemClock.uptimeMillis()-began>3000){finishCheck("別の入力処理が終了していません");return;}handler.postDelayed(()->send(current),50);return;}
  int index=(service.contacts().spread?random.nextInt(points.length/2):requested%(points.length/2))*2;expectedX=points[index];expectedY=points[index+1];Path path=new Path();path.moveTo(expectedX,expectedY);GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,service.tapPressMillis(1))).build();
  requested++;status.setText(getString(R.string.count_of_ten,Integer.toString(requested))+"\n"+getString(R.string.tap_received,Integer.toString(downs),Integer.toString(ups)));
  boolean accepted=service.dispatchTrackedGesture(gesture,new AccessibilityService.GestureResultCallback(){
   public void onCompleted(GestureDescription g){if(current!=ticket)return;completed++;if(requested<10)handler.post(()->send(current));else handler.postDelayed(()->{if(current==ticket)finishCheck("");},150);}
   public void onCancelled(GestureDescription g){if(current==ticket)finishCheck("入力が中断されました");}
  },handler);
  if(!accepted)finishCheck("Androidが入力を受け付けませんでした");
 }
 void finishCheck(String issue){testing=false;ticket++;start.setEnabled(service==TapService.instance);status.setText((issue.isEmpty()&&downs==10&&ups==10&&cancelled==0?"受信確認：10回とも押下・解放を確認":"受信確認："+(issue.isEmpty()?"受信回数を確認してください":issue))+"\n要求 "+requested+" / 送信完了 "+completed+"\n受信：押す "+downs+"・離す "+ups+"・中断 "+cancelled+"\n"+(service==null?"":service.contacts().describe()));}
 void hideControls(){if(controlsHidden||service==null||service!=TapService.instance)return;controlsHidden=true;if(service.panel!=null){panelVisibility=service.panel.getVisibility();service.panel.setVisibility(View.GONE);}markerVisibility=new int[service.marks.size()];for(int i=0;i<markerVisibility.length;i++){markerVisibility[i]=service.marks.get(i).view.getVisibility();service.marks.get(i).view.setVisibility(View.INVISIBLE);}}
 protected void onResume(){super.onResume();hideControls();if(start!=null)start.setEnabled(service!=null&&service==TapService.instance&&!testing);}
 protected void onPause(){if(testing)finishCheck("画面を離れたため中断");ticket++;testing=false;handler.removeCallbacksAndMessages(null);if(controlsHidden&&service!=null&&service==TapService.instance){if(service.panel!=null)service.panel.setVisibility(panelVisibility);if(markerVisibility!=null)for(int i=0;i<Math.min(markerVisibility.length,service.marks.size());i++)service.marks.get(i).view.setVisibility(markerVisibility[i]);}controlsHidden=false;super.onPause();}
}
