package il.org.ay.navigation.ui;

import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * T18c: draws one chevron lying flat on the ground, in perspective, with ARCore's camera
 * matrices. The chevron is a flat "V" in the x/z plane (y = 0), 90 cm wide, pointing along −z;
 * {@link #draw} puts it at a point on the ground and turns it along the route.
 */
class ArGroundArrows {

    private static final String VERTEX_SHADER =
            "uniform mat4 u_Mvp;\n"
            + "attribute vec4 a_Position;\n"
            + "void main() {\n"
            + "  gl_Position = u_Mvp * a_Position;\n"
            + "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n"
            + "uniform vec4 u_Color;\n"
            + "void main() {\n"
            + "  gl_FragColor = u_Color;\n"
            + "}\n";

    /**
     * Two arms of the "V", as four triangles, in metres: 90 cm wide, tip at z = −0.30
     * (forward), arms back to z = 0.15 at x = ±0.45, each 22 cm thick. Big enough to read
     * at 6 m, where perspective makes it thin (checked in a drawing of the projection).
     */
    static final float[] CHEVRON = {
            // left arm
            -0.45f, 0f, 0.15f,   0f, 0f, -0.30f,   0f, 0f, -0.08f,
            -0.45f, 0f, 0.15f,   0f, 0f, -0.08f,   -0.45f, 0f, 0.37f,
            // right arm
            0f, 0f, -0.30f,   0.45f, 0f, 0.15f,   0.45f, 0f, 0.37f,
            0f, 0f, -0.30f,   0.45f, 0f, 0.37f,   0f, 0f, -0.08f,
    };

    /** The app's accent orange (#F57C00). */
    private static final float[] ORANGE = {0.96f, 0.49f, 0f};

    private final FloatBuffer vertices = ByteBuffer.allocateDirect(CHEVRON.length * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(CHEVRON);
    private final float[] model = new float[16];
    private final float[] modelView = new float[16];
    private final float[] mvp = new float[16];
    private int program;
    private int positionAttrib;
    private int mvpUniform;
    private int colorUniform;

    void createOnGlThread() {
        vertices.position(0);
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER));
        GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER));
        GLES20.glLinkProgram(program);
        positionAttrib = GLES20.glGetAttribLocation(program, "a_Position");
        mvpUniform = GLES20.glGetUniformLocation(program, "u_Mvp");
        colorUniform = GLES20.glGetUniformLocation(program, "u_Color");
    }

    /** Call once per frame before the chevrons: no depth test (always on top), see-through edges. */
    void begin() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        vertices.position(0);
        GLES20.glVertexAttribPointer(positionAttrib, 3, GLES20.GL_FLOAT, false, 0, vertices);
        GLES20.glEnableVertexAttribArray(positionAttrib);
    }

    /** One chevron at (x, y, z) in ARCore's world, turned by {@code yawDeg} around the vertical. */
    void draw(float[] view, float[] projection, float x, float y, float z, float yawDeg, float alpha) {
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, x, y, z);
        Matrix.rotateM(model, 0, yawDeg, 0f, 1f, 0f);
        Matrix.multiplyMM(modelView, 0, view, 0, model, 0);
        Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0);
        GLES20.glUniformMatrix4fv(mvpUniform, 1, false, mvp, 0);
        GLES20.glUniform4f(colorUniform, ORANGE[0], ORANGE[1], ORANGE[2], alpha);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, CHEVRON.length / 3);
    }

    void end() {
        GLES20.glDisableVertexAttribArray(positionAttrib);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private static int compile(int type, String code) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);
        return shader;
    }
}
