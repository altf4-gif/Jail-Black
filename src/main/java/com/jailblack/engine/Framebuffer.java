package com.jailblack.engine;

import static org.lwjgl.opengl.GL33.*;

public class Framebuffer {
    private int fboId, textureId, rboId;
    private int width, height;

    public Framebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        init();
    }

    private void init() {
        fboId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureId, 0);

        rboId = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, rboId);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, rboId);

        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer is not complete! Status: " + status);
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        glViewport(0, 0, width, height);
    }

    public void unbind() { glBindFramebuffer(GL_FRAMEBUFFER, 0); }

    public void resize(int newWidth, int newHeight) {
        if (newWidth == width && newHeight == height) return;
        cleanup();
        width = newWidth;
        height = newHeight;
        init();
    }

    public int getTextureId() { return textureId; }
    public int getFboId() { return fboId; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void cleanup() {
        if (rboId != 0) glDeleteRenderbuffers(rboId);
        if (textureId != 0) glDeleteTextures(textureId);
        if (fboId != 0) glDeleteFramebuffers(fboId);
        rboId = textureId = fboId = 0;
    }
}
