/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.service.http.api.domain.entity.multipart;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 *
 */
public interface Part {

  /**
   * Gets the content of this part as an <tt>InputStream</tt>
   *
   * @return The content of this part as an <tt>InputStream</tt>
   * @throws IOException If an error occurs in retrieving the content as an <tt>InputStream</tt>
   */
  InputStream getInputStream() throws IOException;

  /**
   * Gets the content type of this part.
   *
   * @return The content type of this part.
   */
  String getContentType();

  /**
   * Gets the name of this part
   *
   * @return The name of this part as a <tt>String</tt>
   */
  String getName();

  /**
   * Returns the size of this file.
   *
   * @return a <code>long</code> specifying the size of this part, in bytes.
   */
  long getSize();

  /**
   *
   * Returns the value of the specified mime header as a <code>String</code>. If the Part did not include a header of the
   * specified name, this method returns <code>null</code>. If there are multiple headers with the same name, this method returns
   * the first header in the part. The header name is case insensitive. You can use this method with any request header.
   *
   * @param name a <code>String</code> specifying the header name
   *
   * @return a <code>String</code> containing the value of the requested header, or <code>null</code> if the part does not have a
   *         header of that name
   */
  String getHeader(String name);

  /**
   * Gets the values of the Part header with the given name.
   *
   * <p>
   * Any changes to the returned <code>Collection</code> must not affect this <code>Part</code>.
   *
   * <p>
   * Part header names are case insensitive.
   *
   * @param name the header name whose values to return
   *
   * @return a (possibly empty) <code>Collection</code> of the values of the header with the given name
   */
  Collection<String> getHeaders(String name);

  /**
   * Gets the header names of this Part.
   *
   * <p>
   * Some servlet containers do not allow servlets to access headers using this method, in which case this method returns
   * <code>null</code>
   *
   * <p>
   * Any changes to the returned <code>Collection</code> must not affect this <code>Part</code>.
   *
   * @return a (possibly empty) <code>Collection</code> of the header names of this Part
   */
  Collection<String> getHeaderNames();

}
