class UsersController < ApplicationController
  def index 
  end
  def new
    @user = User.new
  end

  def create
    @user = User.new(params[:user])
    @user.login = params[:user][:email]

    # Saving without session maintenance to skip
    # auto-login which can't happen here because
    # the User has not yet been activated
    if @user.save
      flash[:success] = "¡Has creado tu cuenta con exito!"
      @user_session = UserSession.create(:email => @user.email, :password => @user.password)
      redirect_to root_path
    else
      render :action => :new
    end

  end

  def show
    @user = current_user
        @session = current_user
    if !@session
        redirect_to "/"
      end 
  end

  def edit
    @user = current_user
        @session = current_user
    if !@session
        redirect_to "/"
      end 
  end

  def update
    @user = current_user # makes our views "cleaner" and more consistent
     @session = current_user
    if @user.update_attributes(params[:user])
      flash[:success] = "Datos actualizados con exito!"
      redirect_to '/'
    else
      render :action => :edit
    end

  end

  def sudocool
    @user = current_user
    if !@user.nil?
      if @user.email == "mayra.fernanda.melo@gmail.com" or @user.email == "bustamantejj@gmail.com"
        @user.is_admin = 1
        @user.save
        flash[:success] = "Hello master Wayne"
        @user_is_admin = true
      end
    end
    redirect_to "/"
  end

  def show_count
    @user = current_user # makes our views "cleaner" and more consistent
     @session = current_user
    @users = User.all(:joins => :quinielas, :select => "users.name, Users.email, count(quinielas.id) as quinielas_count", :group => "users.id")
  end

end