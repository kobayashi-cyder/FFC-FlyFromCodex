package android.content;
public final class Intent {
 public static final int FLAG_ACTIVITY_NEW_TASK=0x10000000,FLAG_ACTIVITY_CLEAR_TASK=0x8000,FLAG_ACTIVITY_CLEAR_TOP=0x4000000,FLAG_ACTIVITY_SINGLE_TOP=0x20000000;
 public int flags;public Intent addFlags(int value){flags|=value;return this;}
}
