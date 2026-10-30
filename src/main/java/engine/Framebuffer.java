package engine;

import java.nio.ByteBuffer;
import static org.lwjgl.opengl.GL33.*;

public class Framebuffer {
    private final int fboId;
    private final Texture colorTexture;
    private final int rboId;
    private final int width, height;

    public Framebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        fboId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);

        colorTexture = Texture.createEmpty(width, height);
        glF
