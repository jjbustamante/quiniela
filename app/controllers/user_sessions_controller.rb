class UserSessionsController < ApplicationController

  def new
    @user_session = UserSession.new
  end

  def create
    @user_session = UserSession.new(params[:user_session])
    if @user_session.save
      flash[:success] = "¡Bienvenido de nuevo!"
      redirect_back_or_default root_url
    else
      render :action => :new
    end
  end

  def destroy
    current_user_session.destroy
    flash[:success] = "Esperamos que vuelvas pronto!"
    redirect_back_or_default root_url
  end
end